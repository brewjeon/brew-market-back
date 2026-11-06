package com.brewmarket.post;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Component
public class LocalPostImageStorage implements PostImageStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalPostImageStorage.class);
    private final Path root;

    public LocalPostImageStorage(@Value("${app.images.directory:./uploads/post-images}") String directory) {
        root = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    public String save(MultipartFile image) {
        if (image.isEmpty() || image.getSize() > 5 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사진은 5MB 이하로 선택해 주세요.");
        }

        String key = UUID.randomUUID() + ".png";
        Path destination = resolve(key);

        // 확장자와 Content-Type을 믿지 않고 실제 이미지 형식과 크기를 확인한다.
        try (var input = image.getInputStream(); ImageInputStream stream = ImageIO.createImageInputStream(input)) {
            if (stream == null) throw invalidImage();
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw invalidImage();

            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!format.equalsIgnoreCase("JPEG") && !format.equalsIgnoreCase("PNG")) throw invalidImage();

                reader.setInput(stream, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > 12_000_000) throw invalidImage();

                BufferedImage decoded = reader.read(0);
                Files.createDirectories(root);
                // 서버가 다시 인코딩해 원본 파일의 부가 데이터를 그대로 공개하지 않는다.
                if (!ImageIO.write(decoded, "png", destination.toFile())) throw new IOException("PNG writer unavailable");
            } finally {
                reader.dispose();
            }

            return key;
        } catch (IOException exception) {
            delete(key);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사진을 읽거나 저장하지 못했습니다.", exception);
        } catch (RuntimeException exception) {
            delete(key);
            throw exception;
        }
    }

    private ResponseStatusException invalidImage() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "JPEG·PNG 사진만 가능하며 최대 1200만 화소입니다.");
    }

    @Override
    public Path resolve(String key) {
        if (key == null || !key.matches("[0-9a-f-]{36}\\.png")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        return root.resolve(key);
    }

    @Override
    public boolean delete(String key) {
        if (key == null) return true;

        try {
            Files.deleteIfExists(resolve(key));
            return true;
        } catch (IOException exception) {
            log.warn("사진 파일 정리 실패: {}", key, exception);
            return false;
        }
    }
}
