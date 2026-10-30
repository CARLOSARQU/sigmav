package com.sigmav.sigmavbackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * Configura el cliente de Cloudflare R2.
 *
 * R2 es compatible con la API de S3, por eso usamos el SDK de AWS v2.
 * La única diferencia es el endpoint: apunta a R2 en lugar de AWS.
 *
 * Las credenciales se inyectan desde variables de entorno definidas en application.yml:
 *   R2_ACCOUNT_ID, R2_ACCESS_KEY, R2_SECRET_KEY, R2_BUCKET_NAME
 */
@Configuration
public class R2Config {

    @Value("${r2.account-id}")
    private String accountId;

    @Value("${r2.access-key}")
    private String accessKey;

    @Value("${r2.secret-key}")
    private String secretKey;

    /**
     * S3Presigner: genera URLs prefirmadas localmente (sin llamadas HTTP a R2).
     * Solo firma criptográficamente la URL — el archivo nunca pasa por nuestro servidor.
     *
     * Flujo de descarga privada:
     *   1. Backend genera presigned URL (15 min de vida)
     *   2. Frontend redirige al usuario a esa URL
     *   3. R2 valida la firma y sirve el archivo directamente
     *
     * Flujo de subida directa:
     *   1. Backend genera presigned PUT URL
     *   2. Frontend sube el archivo directamente a R2 (sin pasar por el backend)
     *   3. Frontend guarda la key resultante en la BD via POST /api/courses/{id}/materials
     */
    @Bean
    public S3Client s3Client() {
        S3Configuration s3Config = S3Configuration.builder()
                .checksumValidationEnabled(false)
                .build();

        return S3Client.builder()
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .endpointOverride(URI.create("https://" + accountId + ".r2.cloudflarestorage.com"))
                .serviceConfiguration(s3Config)
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        // checksumValidationEnabled(false): R2 no requiere x-amz-content-sha256
        // en las presigned PUT URLs cuando se desactiva la validación de checksum.
        S3Configuration s3Config = S3Configuration.builder()
                .checksumValidationEnabled(false)
                .build();

        return S3Presigner.builder()
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .endpointOverride(URI.create("https://" + accountId + ".r2.cloudflarestorage.com"))
                .serviceConfiguration(s3Config)
                .build();
    }
}
