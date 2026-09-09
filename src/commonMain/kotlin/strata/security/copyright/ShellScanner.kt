package strata.security.copyright

data class ShellScanResult(
    val reverseShells: List<String>,
    val bindShells: List<String>,
    val downloadExec: List<String>,
    val obfuscatedCommands: List<String>,
    val jniJna: List<String>,
    val scriptEngines: List<String>,
    val dynamicCode: List<String>,
    val passed: Boolean
)

object ShellScanner {
    fun scan(classBytes: ByteArray): ShellScanResult {
        val source = classBytes.decodeToString()

        val reverseShells = findReverseShells(source)
        val bindShells = findBindShells(source)
        val downloadExec = findDownloadExec(source)
        val obfuscatedCommands = findObfuscatedCommands(source)
        val jniJna = findJniJna(source)
        val scriptEngines = findScriptEngines(source)
        val dynamicCode = findDynamicCode(source)

        val passed = reverseShells.isEmpty() && bindShells.isEmpty() &&
            downloadExec.isEmpty() && obfuscatedCommands.isEmpty() &&
            jniJna.isEmpty() && scriptEngines.isEmpty() && dynamicCode.isEmpty()

        return ShellScanResult(
            reverseShells = reverseShells,
            bindShells = bindShells,
            downloadExec = downloadExec,
            obfuscatedCommands = obfuscatedCommands,
            jniJna = jniJna,
            scriptEngines = scriptEngines,
            dynamicCode = dynamicCode,
            passed = passed
        )
    }

    private fun findReverseShells(source: String): List<String> {
        val patterns = listOf(
            Regex("bash -i >.*/dev/tcp/\\d+\\.\\d+\\.\\d+\\.\\d+/\\d+"),
            Regex("bash -c '.*/dev/tcp/"),
            Regex("sh -i >.*/dev/tcp/"),
            Regex("mknod .* p && .* 0<&"),
            Regex("python -c 'import socket.*socket\\.connect"),
            Regex("python3 -c 'import socket.*socket\\.connect"),
            Regex("perl -e 'use Socket"),
            Regex("nc -e /bin/sh"),
            Regex("ncat -e /bin/sh"),
            Regex("rm /tmp/f.*mkfifo.*/tmp/f"),
            Regex("socat tcp-connect"),
            Regex("powershell.*New-Object.*Net\\.Sockets\\.TCPClient"),
            Regex("powershell.*System\\.Net\\.Sockets\\.TCPClient"),
            Regex("powershell.*Invoke-Expression.*Net\\.WebClient"),
            Regex("java\\.net\\.Socket.*\\d+\\.\\d+\\.\\d+\\.\\d+"),
            Regex("Socket\\(.*InetAddress\\.getByName"),
            Regex("connect\\(.*InetSocketAddress"),
        )
        return patterns.filter { it.containsMatchIn(source) }.map { it.pattern }
    }

    private fun findBindShells(source: String): List<String> {
        val patterns = listOf(
            Regex("nc -lvp"),
            Regex("ncat -lvp"),
            Regex("socat tcp-listen"),
            Regex("ServerSocket\\(.*bind"),
            Regex("ServerSocket\\(\\d+"),
            Regex("listen\\(.*accept"),
        )
        return patterns.filter { it.containsMatchIn(source) }.map { it.pattern }
    }

    private fun findDownloadExec(source: String): List<String> {
        val patterns = listOf(
            Regex("wget .* && (chmod|bash|sh|./)"),
            Regex("curl .* -o.* && (chmod|bash|sh|./)"),
            Regex("certutil -urlcache -f -split"),
            Regex("bitsadmin /transfer"),
            Regex("powershell.*Invoke-WebRequest.*-OutFile"),
            Regex("powershell.*WebClient\\.DownloadFile"),
            Regex("powershell.*WebClient\\.DownloadString.*Invoke-Expression"),
            Regex("IEX \\(New-Object Net\\.WebClient"),
            Regex("Runtime\\.exec.*wget"),
            Regex("Runtime\\.exec.*curl"),
            Regex("Runtime\\.exec.*powershell"),
            Regex("Runtime\\.getRuntime.*download"),
            Regex("ProcessBuilder.*wget"),
            Regex("ProcessBuilder.*curl"),
            Regex("ProcessBuilder.*powershell"),
            Regex("java\\.net\\.URL.*openStream.*java\\.io\\.File"),
            Regex("DownloadFile|downloadFile|downloadexecute"),
        )
        return patterns.filter { it.containsMatchIn(source) }.map { it.pattern }
    }

    private fun findObfuscatedCommands(source: String): List<String> {
        val patterns = listOf(
            Regex("base64.*-d.*\\|.*bash"),
            Regex("base64.*-d.*\\|.*sh"),
            Regex("echo .* \\| base64 -d"),
            Regex("powershell.*-EncodedCommand"),
            Regex("powershell.*-e "),
            Regex("eval\\(.*base64"),
            Regex("fromCharCode\\(.*\\d+,\\d+,"),
            Regex("\\\\x[0-9a-fA-F]{2}.*\\\\x[0-9a-fA-F]{2}"),
            Regex("sh -c \\\\\".*\\\\\""),
            Regex("System\\.Runtime\\.InteropServices"),
        )
        return patterns.filter { it.containsMatchIn(source) }.map { it.pattern }
    }

    private fun findJniJna(source: String): List<String> {
        val patterns = listOf(
            Regex("System\\.loadLibrary"),
            Regex("Runtime\\.loadLibrary"),
            Regex("System\\.load\\(|Runtime\\.load\\("),
            Regex("java\\.lang\\.System\\.load"),
            Regex("com\\.sun\\.jna"),
            Regex("net\\.java\\.dev\\.jna"),
            Regex("sun\\.jna"),
            Regex("JNIEnv|JavaVM|jni\\.h"),
            Regex("RegisterNatives"),
            Regex("JNI_OnLoad"),
            Regex("native <clinit>"),
        )
        return patterns.filter { it.containsMatchIn(source) }.map { it.pattern }
    }

    private fun findScriptEngines(source: String): List<String> {
        val patterns = listOf(
            Regex("javax\\.script\\.ScriptEngine"),
            Regex("javax\\.script\\.ScriptEngineManager"),
            Regex("nashorn|graal\\.js"),
            Regex("ScriptEngineManager\\.getEngineByName"),
            Regex("eval\\(scriptEngine"),
        )
        return patterns.filter { it.containsMatchIn(source) }.map { it.pattern }
    }

    private fun findDynamicCode(source: String): List<String> {
        val patterns = listOf(
            Regex("java\\.net\\.URLClassLoader"),
            Regex("java\\.lang\\.reflect\\.Proxy"),
            Regex("java\\.lang\\.invoke\\.LambdaMetafactory"),
            Regex("bytecode.*generat|ASM.*ClassWriter"),
            Regex("ClassLoader\\.defineClass"),
            Regex("Unsafe\\.defineClass"),
            Regex("Unsafe\\.defineAnonymousClass"),
            Regex("ClassPool|Javassist"),
            Regex("ByteBuddy|byte-buddy"),
            Regex("cglib|CGLIB"),
        )
        return patterns.filter { it.containsMatchIn(source) }.map { it.pattern }
    }
}
