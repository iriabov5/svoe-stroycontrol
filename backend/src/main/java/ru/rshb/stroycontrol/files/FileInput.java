package ru.rshb.stroycontrol.files;

import static ru.rshb.stroycontrol.common.PreparationInput.field;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import ru.rshb.stroycontrol.common.ApiError;
import ru.rshb.stroycontrol.common.ApiException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

import javax.imageio.ImageIO;

/**
 * Определяет формат по содержимому, ограничивает размер и нормализует имя без доверия MIME клиента.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class FileInput {
    /**
     * Проверяет формат и размер файла до записи объекта.
     *
     * @param data полученные байты
     * @return проверенный MIME
     * @throws ApiException при неподдерживаемом или повреждённом содержимом
     */
    static String contentType(byte[] data) {
        field(data.length > 0, "file", "Выберите непустой файл");
        String mime = null;
        if (data.length >= 8
                && data[0] == (byte) 0x89
                && new String(data, 1, 3, StandardCharsets.US_ASCII).equals("PNG"))
            mime = "image/png";
        else if (data.length > 3
                && data[0] == (byte) 0xff
                && data[1] == (byte) 0xd8
                && data[2] == (byte) 0xff) mime = "image/jpeg";
        else if (data.length >= 20
                && new String(data, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                && new String(data, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) {
            long length =
                    Integer.toUnsignedLong(
                            ByteBuffer.wrap(data, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
            field(length + 8 == data.length, "file", "Повреждённый WebP");
            String chunk = new String(data, 12, 4, StandardCharsets.US_ASCII);
            field(Set.of("VP8 ", "VP8L", "VP8X").contains(chunk), "file", "Повреждённый WebP");
            mime = "image/webp";
        } else if (isPdf(data)) mime = "application/pdf";
        if (mime == null) throw new ApiException("file", "Допустимы JPEG, PNG, WebP и PDF");
        if (data.length > (mime.equals("application/pdf") ? 20 : 10) * 1024 * 1024)
            throw new ApiException(ApiError.ATTACHMENT_TOO_LARGE);
        if (mime.equals("image/png") || mime.equals("image/jpeg")) {
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
                var readers = ImageIO.getImageReaders(input);
                field(readers.hasNext(), "file", "Не удалось прочитать изображение");
                var reader = readers.next();
                try {
                    reader.setInput(input);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    field(
                            width > 0 && height > 0 && (long) width * height <= 40000000,
                            "file",
                            "Изображение должно содержать не больше 40 млн пикселей");
                    field(reader.read(0) != null, "file", "Повреждённое изображение");
                } finally {
                    reader.dispose();
                }
            } catch (IOException _) {
                throw new ApiException("file", "Повреждённое изображение");
            }
        }
        return mime;
    }

    /** Проверяет сигнатуру и конец PDF без декодирования документа в памяти. */
    private static boolean isPdf(byte[] data) {
        return data.length > 10
                && new String(data, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")
                && new String(
                                data,
                                Math.max(0, data.length - 1024),
                                Math.min(data.length, 1024),
                                StandardCharsets.ISO_8859_1)
                        .contains("%%EOF");
    }

    /**
     * Вычисляет стабильный хеш содержимого без хранения исходных байтов в PostgreSQL.
     *
     * @param data содержимое
     * @return SHA-256 в нижнем регистре
     */
    static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Убирает каталоги и управляющие символы из имени скачиваемого файла.
     *
     * @param original клиентское имя либо null
     * @return безопасное имя длиной не более 180 символов
     */
    static String name(String original) {
        String name = original == null ? "Файл" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").strip();
        if (name.isEmpty()) name = "Файл";
        return name.substring(0, Math.min(180, name.length()));
    }
}
