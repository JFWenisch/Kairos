package tech.wenisch.kairos.config;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * Registers GraalVM native image runtime hints for third-party libraries that
 * require additional reflection or resource metadata beyond what Spring Boot's
 * AOT processing generates automatically.
 *
 * <p>Spring Boot 3.x AOT covers most Spring-managed components. The hints here
 * target libraries that use dynamic class loading or custom resource patterns
 * outside Spring's awareness:
 * <ul>
 *   <li>BouncyCastle – JCA provider registration uses reflective constructor
 *       lookup via {@code Security.addProvider()}.</li>
 *   <li>Nimbus JOSE+JWT – processor implementations resolved by class name.</li>
 *   <li>Therapi Runtime Javadoc – reads per-class javadoc resources embedded
 *       by the scribe annotation processor at compile time.</li>
 * </ul>
 */
@Configuration
@ImportRuntimeHints(NativeHintsConfiguration.KairosRuntimeHints.class)
public class NativeHintsConfiguration {

    static class KairosRuntimeHints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            // BouncyCastle: JCA provider registration uses reflective constructor lookup
            registerSafely(hints, classLoader,
                    "org.bouncycastle.jce.provider.BouncyCastleProvider",
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.DECLARED_FIELDS);

            // Nimbus JOSE+JWT: processor implementations may be loaded by class name
            registerSafely(hints, classLoader,
                    "com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier",
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS);
            registerSafely(hints, classLoader,
                    "com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier",
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS);

            // json-smart (transitive via Nimbus): accessed by class name in OAuth2 support
            registerSafely(hints, classLoader,
                    "net.minidev.json.parser.JSONParser",
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS);

            // Therapi runtime javadoc: classpath resources embedded by the scribe processor
            // follow the pattern used by ObjectMapper to deserialise per-class javadoc blobs
            hints.resources().registerPattern("META-INF/therapi-runtime-javadoc-scribe.properties");
        }

        private void registerSafely(RuntimeHints hints, ClassLoader classLoader,
                String className, MemberCategory... categories) {
            try {
                hints.reflection().registerType(classLoader.loadClass(className), categories);
            } catch (ClassNotFoundException ignored) {
                // Not on classpath – skip silently
            }
        }
    }
}
