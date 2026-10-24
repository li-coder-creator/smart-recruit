package com.recruit.smartrecruit.storage;

import com.recruit.smartrecruit.constant.CompanyFileConstants;
import com.recruit.smartrecruit.exception.BusinessException;
import com.recruit.smartrecruit.mapper.CompanyFileMapper;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/** 单机本地存储；文件名由服务端生成，目录不能作为静态资源公开。 */
@Component
@Slf4j
public class CompanyFileStorage {
    public record ValidatedFile(byte[] bytes, String filename, String contentType, String extension) {}
    private final Path root;
    private final CompanyFileMapper mapper;
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private boolean namespaceReady;

    public CompanyFileStorage(@Value("${smart-recruit.company-files.root:./storage/company}") String directory,
                              CompanyFileMapper mapper) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
        this.mapper = mapper;
    }

    public ValidatedFile validate(MultipartFile file, boolean photo) {
        long limit = photo ? CompanyFileConstants.MAX_PHOTO_BYTES : CompanyFileConstants.MAX_QUALIFICATION_BYTES;
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        if (file.getSize() > limit) {
            throw new BusinessException(photo ? "照片不能超过3MB" : "资质材料不能超过10MB");
        }
        String filename = safeFilename(file.getOriginalFilename());
        byte[] bytes;
        try (var input = file.getInputStream()) {
            bytes = input.readNBytes(Math.toIntExact(limit + 1));
        } catch (IOException exception) {
            throw new BusinessException("读取上传文件失败");
        }
        if (bytes.length == 0 || bytes.length > limit) {
            throw new BusinessException("上传文件为空或超过大小限制");
        }
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        boolean pdf = bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P'
                && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-';
        if (pdf && !photo && extension.equals("pdf")) {
            try (var document = Loader.loadPDF(bytes)) {
                if (document.isEncrypted() || document.getNumberOfPages() < 1) {
                    throw new BusinessException("不支持加密或没有页面的PDF");
                }
                // 解析页面树，拒绝只有 PDF 文件头而没有有效页面结构的伪装文件。
                document.getPage(0);
                return new ValidatedFile(bytes, filename, CompanyFileConstants.PDF_TYPE, "pdf");
            } catch (IOException | IllegalArgumentException exception) {
                throw new BusinessException("PDF内容无效或已加密");
            }
        }
        try (var input = new javax.imageio.stream.MemoryCacheImageInputStream(
                new java.io.ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new BusinessException("文件内容不是有效的JPG、PNG或PDF");
            }
            var reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                boolean jpeg = format.equals("jpeg") || format.equals("jpg");
                if (!(format.equals("png") && extension.equals("png"))
                        && !(jpeg && (extension.equals("jpg") || extension.equals("jpeg")))) {
                    throw new BusinessException("文件类型不支持或扩展名与内容不一致");
                }
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels <= 0 || pixels > CompanyFileConstants.MAX_IMAGE_PIXELS) {
                    throw new BusinessException("图片像素总数不能超过2500万");
                }
                if (reader.read(0) == null) {
                    throw new BusinessException("图片内容无效");
                }
                return new ValidatedFile(bytes, filename,
                        jpeg ? CompanyFileConstants.JPEG_TYPE : CompanyFileConstants.PNG_TYPE,
                        jpeg ? "jpg" : "png");
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw new BusinessException("文件内容无效，仅支持JPG、PNG或PDF");
        }
    }

    private String safeFilename(String filename) {
        if (filename == null) throw new BusinessException("文件名不能为空");
        String normalized = filename.replace('\\', '/');
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1).strip();
        if (normalized.isEmpty() || normalized.length() > CompanyFileConstants.MAX_ORIGINAL_FILENAME_LENGTH
                || normalized.chars().anyMatch(c -> c < 32 || c == 127) || !normalized.contains(".")) {
            throw new BusinessException("文件名无效或过长");
        }
        return normalized;
    }

    public String store(ValidatedFile file, boolean photo) {
        requireTransaction();
        ensureNamespace();
        String key = (photo ? CompanyFileConstants.PHOTO_DIRECTORY : CompanyFileConstants.QUALIFICATION_DIRECTORY)
                + "/" + UUID.randomUUID() + "." + file.extension();
        Path path = resolve(key);
        boolean created = false;
        inFlight.add(key);
        try {
            Files.createDirectories(path.getParent());
            Files.createFile(path);
            created = true;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    try {
                        if (status == STATUS_ROLLED_BACK) Files.deleteIfExists(path);
                    } catch (IOException exception) {
                        log.error("上传回滚文件清理失败，将在下次启动时核对: {}", key, exception);
                    } finally {
                        inFlight.remove(key);
                    }
                }
            });
            Files.write(path, file.bytes(), StandardOpenOption.TRUNCATE_EXISTING);
            return key;
        } catch (IOException | RuntimeException exception) {
            if (created) {
                try { Files.deleteIfExists(path); }
                catch (IOException cleanup) { log.error("文件写入补偿清理失败: {}", key, cleanup); }
            }
            inFlight.remove(key);
            throw new BusinessException("文件保存失败，请稍后重试");
        }
    }

    /** 暂存待删除照片，事务回滚恢复；提交后清理。崩溃残留在启动时按数据库引用恢复或清理。 */
    public void quarantinePhoto(String key) {
        requireTransaction();
        ensureNamespace();
        Path original = resolve(key);
        Path trash = root.resolve(CompanyFileConstants.TRASH_DIRECTORY).resolve(original.getFileName());
        if (!Files.exists(original)) return; // 原文件已丢失时仍允许移除不可用的照片记录。
        boolean moved = false;
        inFlight.add(key);
        try {
            Files.createDirectories(trash.getParent());
            Files.move(original, trash);
            moved = true;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    try {
                        if (status == STATUS_COMMITTED) Files.deleteIfExists(trash);
                        else if (status == STATUS_ROLLED_BACK) Files.move(trash, original);
                    } catch (IOException exception) {
                        log.error("照片文件事务补偿未完成，下次启动继续核对: {}", key, exception);
                    } finally { inFlight.remove(key); }
                }
            });
        } catch (IOException | RuntimeException exception) {
            if (moved) {
                try { Files.move(trash, original); }
                catch (IOException restore) { log.error("照片恢复失败: {}", key, restore); }
            }
            inFlight.remove(key);
            throw new BusinessException("照片文件移除失败，请稍后重试");
        }
    }

    public byte[] read(String key, long limit) {
        ensureNamespace();
        Path path = resolve(key);
        try (var input = Files.newInputStream(path)) {
            if (!Files.isRegularFile(path) || Files.size(path) > limit) {
                throw new BusinessException("文件不可用");
            }
            byte[] bytes = input.readNBytes(Math.toIntExact(limit + 1));
            if (bytes.length == 0 || bytes.length > limit) throw new BusinessException("文件不可用");
            return bytes;
        } catch (IOException exception) {
            throw new BusinessException("文件不存在或暂不可读取");
        }
    }

    private Path resolve(String key) {
        if (key == null || !key.matches("(photos|qualifications)/[0-9a-f-]{36}\\.(png|jpg|pdf)")) {
            throw new BusinessException("文件存储引用无效");
        }
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) throw new BusinessException("文件路径无效");
        return path;
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("文件写操作必须处于数据库事务中");
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverLocalFiles() {
        ensureNamespace();
        // 仅检查本模块生成的 UUID 文件；已有数据库引用（含已移除资质）必须保留。
        Path trash = root.resolve(CompanyFileConstants.TRASH_DIRECTORY);
        if (Files.isDirectory(trash)) {
            try (var files = Files.list(trash)) {
                files.filter(Files::isRegularFile).forEach(path -> {
                    String key = CompanyFileConstants.PHOTO_DIRECTORY + "/" + path.getFileName();
                    if (inFlight.contains(key) || !generatedKey(key)) return;
                    try {
                        if (mapper.isReferenced(key)) {
                            Files.createDirectories(resolve(key).getParent());
                            Files.move(path, resolve(key));
                        } else Files.deleteIfExists(path);
                    } catch (Exception exception) { log.error("待删除照片恢复核对失败: {}", key, exception); }
                });
            } catch (IOException exception) { log.error("读取照片回收目录失败", exception); }
        }
        for (String directory : List.of(CompanyFileConstants.PHOTO_DIRECTORY, CompanyFileConstants.QUALIFICATION_DIRECTORY)) {
            Path folder = root.resolve(directory);
            if (!Files.isDirectory(folder)) continue;
            try (var files = Files.list(folder)) {
                files.filter(Files::isRegularFile).forEach(path -> {
                    String key = directory + "/" + path.getFileName();
                    if (inFlight.contains(key) || !generatedKey(key)) return;
                    try {
                        if (!mapper.isReferenced(key)) Files.deleteIfExists(path);
                    } catch (Exception exception) { log.error("孤儿文件核对失败: {}", key, exception); }
                });
            } catch (IOException exception) { log.error("读取文件目录失败: {}", directory, exception); }
        }
    }

    private boolean generatedKey(String key) {
        return key.matches("(photos|qualifications)/[0-9a-f-]{36}\\.(png|jpg|pdf)");
    }

    /** 防止切换数据库却复用同一文件目录，进而误清理另一个数据库的原件。 */
    private synchronized void ensureNamespace() {
        if (namespaceReady) return;
        String database = mapper.databaseName();
        Path marker = root.resolve(".database");
        try {
            Files.createDirectories(root);
            try {
                Files.writeString(marker, database, StandardOpenOption.CREATE_NEW);
            } catch (FileAlreadyExistsException ignored) {
                // 已绑定的存储目录只能被同一个数据库使用。
            }
            if (!database.equals(Files.readString(marker))) {
                throw new IllegalStateException("文件存储目录已绑定其他数据库，请配置独立目录");
            }
            namespaceReady = true;
        } catch (IOException exception) {
            throw new IllegalStateException("文件存储目录不可用", exception);
        }
    }
}
