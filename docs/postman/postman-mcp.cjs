const path=require('path');
const runtime=process.env.POSTMAN_MCP_RUNTIME||path.resolve('..','.mcp','postman','node_modules');
const {Client}=require(path.join(runtime,'@modelcontextprotocol/sdk/dist/cjs/client/index.js'));
const {StdioClientTransport}=require(path.join(runtime,'@modelcontextprotocol/sdk/dist/cjs/client/stdio.js'));
function data(result){if(result.isError)throw Error(JSON.stringify(result));return result.structuredContent??JSON.parse(result.content.find(x=>x.type==='text').text);}
async function connect(){
    const client=new Client({name:'smartrecruit-application-event-regression',version:'1.0'});
    await client.connect(new StdioClientTransport({command:process.execPath,
        args:[path.join(runtime,'@postman/postman-mcp-server/dist/src/index.js'),'--full'],env:process.env,stderr:'ignore'}));
    await client.readResource({uri:'postman://instructions'});
    return client;
}
module.exports={connect,data};
