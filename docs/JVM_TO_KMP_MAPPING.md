# JVM → KMP API Mapping for Security Framework

## Overview
The security framework was written for JVM. We're converting it to Kotlin Multiplatform (KMP).

---

## Crypto APIs

| JVM API | KMP Equivalent | Library |
|---------|---------------|---------|
| `javax.crypto.Cipher` (AES/GCM) | `expect fun aesGcmEncrypt/Decrypt()` | `cryptography-kotlin` or `expect/actual` |
| `java.security.MessageDigest` (SHA-256) | `SHA256().digest()` | `org.kotlincrypto.hash.sha2:2.7.1` |
| `java.security.MessageDigest` (SHA-384) | `SHA384().digest()` | `org.kotlincrypto.hash.sha2:2.7.1` |
| `java.security.MessageDigest` (SHA-512) | `SHA512().digest()` | `org.kotlincrypto.hash.sha2:2.7.1` |
| `java.security.SecureRandom` | `expect fun secureRandomBytes()` | `expect/actual` |
| `java.security.KeyPairGenerator` (Ed25519) | `expect fun generateEd25519KeyPair()` | `expect/actual` |
| `java.security.Signature` (Ed25519) | `expect fun sign/verify()` | `expect/actual` |
| `javax.crypto.KEM` (ML-KEM-768) | `expect fun kemEncapsulate/Decapsulate()` | `expect/actual` (PQC) |
| `javax.crypto.spec.SecretKeySpec` | `ByteArray` (raw key) | Native |
| `javax.crypto.spec.GCMParameterSpec` | `ByteArray` (IV) + `Int` (tag bits) | Native |

## Encoding APIs

| JVM API | KMP Equivalent | Library |
|---------|---------------|---------|
| `java.util.Base64.getEncoder()` | `encodeBase64()` | Manual or `kotlinx-io` |
| `java.util.Base64.getDecoder()` | `decodeBase64()` | Manual or `kotlinx-io` |

## File I/O APIs

| JVM API | KMP Equivalent | Library |
|---------|---------------|---------|
| `java.io.File` | `expect class PlatformFile` | `expect/actual` |
| `java.io.RandomAccessFile` | `expect fun secureWrite()` | `expect/actual` |
| `java.util.jar.JarFile` | `expect fun inspectJar()` | `expect/actual` |
| `java.util.jar.JarInputStream` | `expect fun streamJar()` | `expect/actual` |

## Concurrency APIs

| JVM API | KMP Equivalent | Library |
|---------|---------------|---------|
| `java.util.concurrent.ConcurrentHashMap` | `AtomicRef` + `synchronized` | `kotlinx.atomicfu` |
| `kotlinx.coroutines.*` | ✅ Already KMP! | `kotlinx-coroutines-core:1.11.0` |

## FFM APIs (Java 25)

| JVM API | KMP Equivalent | Library |
|---------|---------------|---------|
| `java.lang.foreign.MemorySegment` | `ByteArray` | Native |
| `java.lang.foreign.Arena` | Manual allocation | Native |
| `java.lang.foreign.Linker` | `platform.posix` / `platform.windows` | Native interop |

## Process APIs

| JVM API | KMP Equivalent | Library |
|---------|---------------|---------|
| `Runtime.getRuntime().exec()` | `expect fun executeProcess()` | `expect/actual` |
| `ProcessBuilder` | `expect fun executeProcess()` | `expect/actual` |
| `System.getenv()` | `platform.posix.getenv()` | Native |
| `System.getProperty()` | N/A | Not needed |

## Reflection APIs

| JVM API | KMP Equivalent | Library |
|---------|---------------|---------|
| `Class.forName()` | N/A | Not needed in KMP |
| `MethodHandle` | N/A | Use function references |

## String APIs

| JVM API | KMP Equivalent | Library |
|---------|---------------|---------|
| `String.toByteArray(Charsets.UTF_8)` | `encodeToByteArray()` | ✅ Already KMP |
| `ByteArray.toString(Charsets.UTF_8)` | `decodeToString()` | ✅ Already KMP |

---

## Implementation Strategy

### Phase 1: Core (No Crypto)
Files that compile without changes:
- `SecurityResult.kt` ✅
- `SecurityContext.kt` ✅
- `SecurityGateway.kt` ✅
- `DevConfig.kt` ✅ (fix System.getenv)
- `policy/SecurityRule.kt` ✅
- `policy/SexualRule.kt` ✅
- `policy/ChatGuardian.kt` ✅
- `policy/PVPIntegrityRule.kt` ✅
- `policy/ModdingRule.kt` ✅ (fix ByteArray.decodeToString)
- `iiv/IIVQuestionnaire.kt` ✅
- `iiv/IIVDecision.kt` ✅
- `copyright/CopyrightRegistry.kt` ✅
- `copyright/MinecraftSignatureDB.kt` ✅
- `copyright/ModCategorizer.kt` ✅ (fix Regex)
- `copyright/ContentProtectionRule.kt` ✅

### Phase 2: Crypto (expect/actual)
Files needing expect/actual:
- `crypto/RKPManager.kt` → expect in commonMain, actual in desktopMain
- `crypto/PQCProvider.kt` → expect in commonMain, actual in desktopMain
- `protocol/SecureTunnel.kt` → expect in commonMain, actual in desktopMain

### Phase 3: File I/O (expect/actual)
Files needing expect/actual:
- `Shredder.kt` → expect in commonMain, actual in desktopMain
- `AntiInjection.kt` → expect in commonMain, actual in desktopMain
- `memory/MemoryIsolator.kt` → expect in commonMain, actual in desktopMain
- `memory/SecureMemoryArena.kt` → expect in commonMain, actual in desktopMain
- `copyright/JarAnalyzer.kt` → expect in commonMain, actual in desktopMain
- `copyright/ModScanner.kt` → expect in commonMain, actual in desktopMain
- `copyright/ModFolderScanner.kt` → expect in commonMain, actual in desktopMain
- `copyright/HsvAnalyzer.kt` → expect in commonMain, actual in desktopMain
- `copyright/AssetImageScanner.kt` → expect in commonMain, actual in desktopMain
- `copyright/ShellScanner.kt` → expect in commonMain, actual in desktopMain

---

## KMP Libraries to Add

```kotlin
// build.gradle.kts
commonMain.dependencies {
    // Hashing
    implementation("org.kotlincrypto:cryptography-hash-sha2:2.7.1")
    implementation("org.kotlincrypto:cryptography-hash-sha3:2.7.1")
    implementation("org.kotlincrypto:cryptography-core:2.7.1")
    
    // Coroutines (already present)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    
    // Serialization (for JSON config)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
```

---

## Package Structure (Final)

```
src/commonMain/kotlin/strata/security/
├── SecurityResult.kt          # ✅ Pure Kotlin
├── SecurityContext.kt         # ✅ Pure Kotlin
├── SafetyGuardian.kt          # ✅ Pure Kotlin (orchestrator)
├── DefaultSecurityGateway.kt  # ✅ Pure Kotlin
├── ReputationManager.kt       # ✅ Pure Kotlin
├── DevConfig.kt               # ⚠️ Fix System.getenv → expect/actual
├── AntiInjection.kt           # ❌ Needs expect/actual (prctl, File)
├── Shredder.kt                # ❌ Needs expect/actual (File I/O)
├── policy/
│   ├── SecurityRule.kt        # ✅ Pure Kotlin
│   ├── SexualRule.kt          # ✅ Pure Kotlin
│   ├── ChatGuardian.kt        # ✅ Pure Kotlin
│   ├── PVPIntegrityRule.kt    # ✅ Pure Kotlin
│   ├── ModdingRule.kt         # ✅ Pure Kotlin
│   ├── IIVRule.kt             # ✅ Pure Kotlin
│   ├── AVCManager.kt          # ✅ Pure Kotlin
│   ├── BuildIntegrityRule.kt  # ✅ Pure Kotlin
│   └── SocialTrustMonitor.kt  # ✅ Pure Kotlin
├── iiv/
│   ├── IIVQuestionnaire.kt    # ✅ Pure Kotlin
│   ├── IIVDecision.kt         # ✅ Pure Kotlin
│   ├── IIVEngine.kt           # ✅ Pure Kotlin
│   ├── IIVGate.kt             # ✅ Pure Kotlin
│   └── IIVToken.kt            # ✅ Pure Kotlin
├── copyright/
│   ├── CopyrightRegistry.kt   # ✅ Pure Kotlin
│   ├── MinecraftSignatureDB.kt # ✅ Pure Kotlin
│   ├── ModCategorizer.kt      # ✅ Pure Kotlin
│   ├── ContentProtectionRule.kt # ✅ Pure Kotlin
│   ├── JarAnalyzer.kt         # ❌ Needs expect/actual (JarFile)
│   ├── ModScanner.kt          # ❌ Needs expect/actual (JarFile)
│   ├── ModFolderScanner.kt    # ❌ Needs expect/actual (File)
│   ├── HsvAnalyzer.kt         # ❌ Needs expect/actual (Image parsing)
│   ├── AssetImageScanner.kt   # ❌ Needs expect/actual (Image parsing)
│   ├── NsfwWordDB.kt          # ✅ Pure Kotlin (data only)
│   └── ShellScanner.kt        # ❌ Needs expect/actual (Process)
├── crypto/
│   ├── RKPManager.kt          # ❌ Needs expect/actual (Ed25519)
│   └── PQCProvider.kt         # ❌ Needs expect/actual (AES-GCM, ML-KEM)
├── memory/
│   ├── MemoryIsolator.kt      # ❌ Needs expect/actual (FFM)
│   └── SecureMemoryArena.kt   # ❌ Needs expect/actual (FFM)
└── protocol/
    └── SecureTunnel.kt        # ❌ Needs expect/actual (AES-GCM)
```

**Summary:**
- ✅ 22 files are pure Kotlin (already KMP compatible!)
- ❌ 14 files need expect/actual conversions
- ⚠️ 1 file needs minor fix
