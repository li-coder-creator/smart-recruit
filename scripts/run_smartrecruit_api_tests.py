import csv
import json
import time
import urllib.error
import urllib.request
from pathlib import Path


BASE_URL = "http://localhost:8081"
RESULTS = []


def request(case_id, name, method, path, body=None, token=None, expected_http=200,
            expected_code=None, expected_message_contains=None, test_type="normal",
            priority="P1", skip=False, skip_reason=""):
    if skip:
        RESULTS.append({
            "caseId": case_id,
            "name": name,
            "method": method,
            "path": path,
            "type": test_type,
            "priority": priority,
            "status": "SKIPPED",
            "httpStatus": "",
            "code": "",
            "message": skip_reason,
            "detail": skip_reason,
        })
        return None

    data = None
    headers = {}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json"

    req = urllib.request.Request(BASE_URL + path, data=data, headers=headers, method=method)
    http_status = 0
    text = ""
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            http_status = resp.status
            text = resp.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as exc:
        http_status = exc.code
        text = exc.read().decode("utf-8", errors="replace")
    except Exception as exc:
        text = str(exc)

    parsed = None
    if text:
        try:
            parsed = json.loads(text)
        except Exception:
            parsed = None

    ok = True
    detail = []
    if http_status != expected_http:
        ok = False
        detail.append(f"HTTP expected {expected_http}, got {http_status}")
    if expected_code is not None:
        actual = parsed.get("code") if isinstance(parsed, dict) else None
        if actual != expected_code:
            ok = False
            detail.append(f"body.code expected {expected_code}, got {actual}")
    if expected_message_contains:
        actual_message = str(parsed.get("message", "")) if isinstance(parsed, dict) else text
        if expected_message_contains not in actual_message:
            ok = False
            detail.append(f"message expected contains {expected_message_contains!r}, got {actual_message!r}")

    RESULTS.append({
        "caseId": case_id,
        "name": name,
        "method": method,
        "path": path,
        "type": test_type,
        "priority": priority,
        "status": "PASSED" if ok else "FAILED",
        "httpStatus": http_status,
        "code": parsed.get("code") if isinstance(parsed, dict) else "",
        "message": parsed.get("message") if isinstance(parsed, dict) else text[:300],
        "detail": "; ".join(detail) if detail else "OK",
    })
    return parsed


def find_first(items, key, value):
    for item in items or []:
        if isinstance(item, dict) and item.get(key) == value:
            return item
    return None


def main():
    stamp = time.strftime("%m%d%H%M%S")
    applicant_username = f"ta{stamp}"
    company_username = f"tc{stamp}"
    password = "Test_12345"
    resume_title = f"api-resume-{stamp}"
    job_title = f"api-job-{stamp}"

    request("AUTH-015", "protected endpoint without token", "GET", "/user/info",
            expected_http=401, test_type="auth", priority="P0")

    request("AUTH-001", "register applicant", "POST", "/user/register",
            {"username": applicant_username, "password": password}, expected_code=200, priority="P0")
    request("AUTH-010", "duplicate applicant username", "POST", "/user/register",
            {"username": applicant_username, "password": password}, expected_code=500,
            expected_message_contains="用户名已存在", test_type="business-error", priority="P0")
    request("AUTH-004", "username too short", "POST", "/user/register",
            {"username": "abcd", "password": password}, expected_code=500,
            expected_message_contains="用户名必须", test_type="boundary", priority="P0")

    login_applicant = request("AUTH-011", "login applicant", "POST", "/user/login",
                              {"username": applicant_username, "password": password}, expected_code=200, priority="P0")
    applicant_token = login_applicant.get("data") if isinstance(login_applicant, dict) else None

    request("AUTH-013", "login wrong password", "POST", "/user/login",
            {"username": applicant_username, "password": "Wrong_12345"}, expected_code=500,
            expected_message_contains="用户名或密码错误", test_type="error", priority="P0")

    request("USER-001", "get applicant info", "GET", "/user/info",
            token=applicant_token, expected_code=200, priority="P0")
    request("USER-003", "update applicant profile", "PUT", "/user/info",
            {"username": applicant_username, "nickname": "api tester", "email": "api@example.com", "phone": "13800138000"},
            token=applicant_token, expected_code=200, priority="P0")
    request("USER-004", "update invalid email", "PUT", "/user/info",
            {"username": applicant_username, "nickname": "api tester", "email": "bad-email", "phone": "13800138000"},
            token=applicant_token, expected_code=500, expected_message_contains="邮箱格式不正确",
            test_type="format-error", priority="P0")

    register_company = request("COMP-001", "register company", "POST", "/company/register",
                               {"username": company_username, "password": password, "companyName": f"API Test Company {stamp}",
                                "city": "Shanghai", "address": "Test Road"},
                               expected_code=200, priority="P0")
    company_id = None
    if isinstance(register_company, dict) and isinstance(register_company.get("data"), dict):
        company_id = register_company["data"].get("companyId")

    login_company = request("AUTH-COMP", "login company", "POST", "/user/login",
                            {"username": company_username, "password": password}, expected_code=200, priority="P0")
    company_token = login_company.get("data") if isinstance(login_company, dict) else None

    request("COMP-008", "get company before approval", "GET", "/company",
            token=company_token, expected_code=200, priority="P1")
    request("JOB-002", "pending company cannot publish job", "POST", "/job",
            {"title": job_title, "description": "Spring Boot", "salaryMin": 10000, "salaryMax": 20000,
             "city": "Shanghai", "experience": "1-3", "education": "Bachelor", "status": 0},
            token=company_token, expected_code=500, expected_message_contains="企业未通过验证",
            test_type="business-error", priority="P0")

    login_admin = request("AUTH-ADMIN", "login admin", "POST", "/user/login",
                          {"username": "admin", "password": "123456"}, expected_code=200, priority="P0")
    admin_token = login_admin.get("data") if isinstance(login_admin, dict) else None

    request("ADMIN-001", "pending company list", "GET", "/admin/company/pending?page=1&pageSize=10",
            token=admin_token, expected_code=200, priority="P0")
    if company_id:
        request("ADMIN-008", "company detail", "GET", f"/admin/company/{company_id}",
                token=admin_token, expected_code=200, priority="P0")
        request("ADMIN-010", "approve generated company", "PUT", f"/admin/company/{company_id}/approve",
                token=admin_token, expected_code=200, test_type="state-change", priority="P0")
    else:
        request("ADMIN-008", "company detail", "GET", "/admin/company/{{companyId}}", skip=True,
                skip_reason="companyId was not resolved", priority="P0")

    request("JOB-001", "approved company publishes job", "POST", "/job",
            {"title": job_title, "description": "Spring Boot/MyBatis/Redis", "salaryMin": 10000, "salaryMax": 20000,
             "city": "Shanghai", "experience": "1-3", "education": "Bachelor", "status": 0},
            token=company_token, expected_code=200, priority="P0")

    company_jobs = request("JOB-009", "company job list", "GET", "/job/company",
                           token=company_token, expected_code=200, priority="P0")
    job = find_first(company_jobs.get("data") if isinstance(company_jobs, dict) else [], "title", job_title)
    job_id = job.get("id") if job else None

    request("JOB-007A", "job list requires auth", "GET", "/job",
            expected_http=401, test_type="auth", priority="P0")
    request("JOB-007", "job list with applicant token", "GET", "/job",
            token=applicant_token, expected_code=200, priority="P0")
    if job_id:
        request("JOB-011", "job detail", "GET", f"/job/{job_id}", token=applicant_token, expected_code=200, priority="P0")
    else:
        request("JOB-011", "job detail", "GET", "/job/{{jobId}}", skip=True,
                skip_reason="jobId was not resolved", priority="P0")
    request("JOB-012", "missing job detail", "GET", "/job/99999999",
            token=applicant_token, expected_code=500, expected_message_contains="岗位不存在", test_type="error", priority="P0")

    request("RES-001", "create resume", "POST", "/resume",
            {"title": resume_title, "description": "Spring Boot resume", "fileUrl": "https://example.com/resume.pdf"},
            token=applicant_token, expected_code=200, priority="P0")
    resume_list = request("RES-005", "resume list", "GET", "/resume",
                          token=applicant_token, expected_code=200, priority="P0")
    resume = find_first(resume_list.get("data") if isinstance(resume_list, dict) else [], "title", resume_title)
    resume_id = resume.get("id") if resume else None

    if resume_id:
        request("RES-007", "resume detail", "GET", f"/resume/{resume_id}",
                token=applicant_token, expected_code=200, priority="P0")
        request("EDU-001", "create education", "POST", f"/resume/{resume_id}/education",
                {"schoolName": "Test University", "degree": "Bachelor", "major": "Software Engineering",
                 "startDate": "2020-09-01", "endDate": "2024-06-30", "gpa": "3.8", "sortOrder": 0},
                token=applicant_token, expected_code=200, priority="P0")
        request("EDU-006", "education list", "GET", f"/resume/{resume_id}/education",
                token=applicant_token, expected_code=200, priority="P0")
    else:
        request("RES-007", "resume detail", "GET", "/resume/{{resumeId}}", skip=True,
                skip_reason="resumeId was not resolved", priority="P0")

    request("RES-002", "company cannot create resume", "POST", "/resume",
            {"title": "Bad resume", "description": "Should fail"},
            token=company_token, expected_code=500, expected_message_contains="仅求职者可访问",
            test_type="permission", priority="P0")

    application_id = None
    if job_id and resume_id:
        request("APP-001", "apply job", "POST", "/application",
                {"jobId": job_id, "resumeId": resume_id}, token=applicant_token, expected_code=200, priority="P0")
        request("APP-009", "duplicate apply", "POST", "/application",
                {"jobId": job_id, "resumeId": resume_id}, token=applicant_token, expected_code=500,
                expected_message_contains="请勿重复投递同一岗位", test_type="duplicate", priority="P0")
        mine = request("APP-010", "applicant application list", "GET", "/application/mine",
                       token=applicant_token, expected_code=200, priority="P0")
        app = find_first(mine.get("data") if isinstance(mine, dict) else [], "jobId", job_id)
        application_id = app.get("id") if app else None
        request("APP-012", "company application list", "GET", "/application/my",
                token=company_token, expected_code=200, priority="P0")
        if application_id:
            request("APP-014", "application detail as applicant", "GET", f"/application/{application_id}",
                    token=applicant_token, expected_code=200, priority="P0")
            request("APP-019", "update application status to interview", "PUT", f"/application/{application_id}/status",
                    {"status": 2}, token=company_token, expected_code=200, test_type="state-change", priority="P0")
            request("APP-020", "application status out of range", "PUT", f"/application/{application_id}/status",
                    {"status": 5}, token=company_token, expected_code=500,
                    expected_message_contains="投递状态不能大于4", test_type="boundary", priority="P0")
    else:
        request("APP-001", "apply job", "POST", "/application", skip=True,
                skip_reason="missing generated jobId or resumeId", priority="P0")

    request("APP-013", "applicant cannot view company applications", "GET", "/application/my",
            token=applicant_token, expected_code=500, expected_message_contains="仅企业用户可访问",
            test_type="permission", priority="P0")
    request("ADMIN-004", "applicant cannot query pending companies", "GET", "/admin/company/pending",
            token=applicant_token, expected_code=500, expected_message_contains="仅管理员可访问",
            test_type="permission", priority="P0")
    request("ADMIN-005", "admin company list", "GET", "/admin/company?page=1&pageSize=10",
            token=admin_token, expected_code=200, priority="P0")
    request("ADMIN-006", "admin company filter approved", "GET", "/admin/company?page=1&pageSize=10&companyStatus=APPROVED",
            token=admin_token, expected_code=200, test_type="filter", priority="P0")
    request("ADMIN-007", "admin company invalid status", "GET", "/admin/company?companyStatus=UNKNOWN",
            token=admin_token, expected_code=500, test_type="format-error", priority="P1")
    request("ADMIN-015", "admin user list", "GET", "/admin/user?page=1&pageSize=10",
            token=admin_token, expected_code=200, priority="P0")
    request("ADMIN-016", "admin user filter company", "GET", "/admin/user?page=1&pageSize=10&userRole=COMPANY",
            token=admin_token, expected_code=200, test_type="filter", priority="P0")
    request("ADMIN-017", "admin user invalid role", "GET", "/admin/user?userRole=USER",
            token=admin_token, expected_code=500, test_type="format-error", priority="P1")

    request("USER-007", "change password", "PATCH", "/user/password", skip=True,
            skip_reason="Skipped to avoid changing credentials", test_type="high-risk", priority="P0")
    request("JOB-017", "delete job", "DELETE", "/job/{{jobId}}", skip=True,
            skip_reason="Skipped to preserve generated audit data", test_type="high-risk", priority="P1")
    request("RES-013", "delete resume", "DELETE", "/resume/{{resumeId}}", skip=True,
            skip_reason="Skipped to preserve generated audit data", test_type="high-risk", priority="P1")
    request("COMP-012", "delete company", "DELETE", "/company", skip=True,
            skip_reason="Skipped to preserve generated audit data", test_type="high-risk", priority="P1")

    out_dir = Path("test-results")
    out_dir.mkdir(exist_ok=True)
    json_path = out_dir / "smartrecruit-api-test-results.json"
    csv_path = out_dir / "smartrecruit-api-test-results.csv"
    json_path.write_text(json.dumps(RESULTS, ensure_ascii=False, indent=2), encoding="utf-8")
    with csv_path.open("w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=list(RESULTS[0].keys()))
        writer.writeheader()
        writer.writerows(RESULTS)

    summary = {
        "total": len(RESULTS),
        "passed": sum(1 for r in RESULTS if r["status"] == "PASSED"),
        "failed": sum(1 for r in RESULTS if r["status"] == "FAILED"),
        "skipped": sum(1 for r in RESULTS if r["status"] == "SKIPPED"),
        "json": str(json_path.resolve()),
        "csv": str(csv_path.resolve()),
    }
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
