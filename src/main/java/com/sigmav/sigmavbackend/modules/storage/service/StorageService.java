package com.sigmav.sigmavbackend.modules.storage.service;

import com.sigmav.sigmavbackend.modules.storage.dto.UploadUrlResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

/**
 * Servicio de almacenamiento privado sobre Cloudflare R2.
 *
 * Genera URLs prefirmadas (presigned URLs) que permiten al usuario
 * descargar o subir archivos directamente desde/hacia R2, sin que
 * el archivo transite por nuestro backend.
 *
 * Seguridad:
 * - El bucket de R2 es PRIVADO (sin acceso público)
 * - Las URLs de descarga expiran en `presignedUrlExpiryMinutes` (default 15 min)
 * - Las URLs de subida expiran en 10 minutos
 * - El acceso a cada URL se valida en la capa de servicio ANTES de generar la URL
 */
@Service
public class StorageService {

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final String bucket;
    private final String publicBucket;
    private final int expiryMinutes;
    private final String publicUrl;

    public StorageService(
            S3Client s3Client,
            S3Presigner presigner,
            @Value("${r2.bucket-name}") String bucket,
            @Value("${r2.public-bucket-name:${r2.bucket-name}}") String publicBucket,
            @Value("${r2.presigned-url-expiry-minutes:15}") int expiryMinutes,
            @Value("${r2.public-url:}") String publicUrl) {
        this.s3Client = s3Client;
        this.presigner = presigner;
        this.bucket = bucket;
        this.publicBucket = publicBucket;
        this.expiryMinutes = expiryMinutes;
        this.publicUrl = publicUrl;
    }

    /**
     * Genera una URL de descarga prefirmada para un objeto privado en R2.
     *
     * @param key  Clave del objeto en el bucket (ej: "1/courses/5/abc.pdf")
     * @return     URL temporal válida por `expiryMinutes` minutos
     */
    public String generateDownloadUrl(String key) {
        GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(expiryMinutes))
                .getObjectRequest(r -> r.bucket(bucket).key(key))
                .build();
        return presigner.presignGetObject(request).url().toString();
    }

    /**
     * Genera una URL de subida prefirmada para que el frontend suba un archivo
     * directamente a R2 sin pasar por el backend.
     *
     * El frontend debe hacer PUT a esta URL con el archivo en el cuerpo.
     * El Content-Type enviado debe coincidir con el especificado aquí.
     *
     * @param key          Clave del objeto en el bucket (generada por el backend)
     * @param contentType  MIME type del archivo (ej: "application/pdf")
     * @return             UploadUrlResponse con la URL de subida y la key para guardar en BD
     */
    public UploadUrlResponse generateUploadUrl(String key, String contentType) {
        PutObjectPresignRequest request = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10))
                .putObjectRequest(r -> r.bucket(bucket).key(key).contentType(contentType))
                .build();
        String uploadUrl = presigner.presignPutObject(request).url().toString();
        return new UploadUrlResponse(uploadUrl, key);
    }

    /**
     * Sube un logo directamente a R2 desde el backend y devuelve la URL pública permanente.
     *
     * @param key          Clave del objeto (ej: "logos/1/abc123.png")
     * @param bytes        Contenido del archivo
     * @param contentType  MIME type de la imagen
     * @return             URL pública permanente para guardar en BD
     */
    public String uploadLogo(String key, byte[] bytes, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(publicBucket)
                .key(key)
                .contentType(contentType)
                .contentLength((long) bytes.length)
                .build();
        s3Client.putObject(request, RequestBody.fromBytes(bytes));
        return publicUrl + "/" + key;
    }

    public int getExpiryMinutes() {
        return expiryMinutes;
    }
}
