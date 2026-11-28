// Chunk/Wasm URLs follow the selected version's script, never the page/bridge base URL.
config.output.publicPath = 'auto';
// Kotlin 的 DevServer DSL 尚未暴露 host；仅允许本机访问开发服务器。
if (config.devServer) {
    config.devServer.host = '127.0.0.1';
    config.devServer.allowedHosts = ['127.0.0.1'];
    config.devServer.headers = {'Cache-Control': 'no-store'};
}
