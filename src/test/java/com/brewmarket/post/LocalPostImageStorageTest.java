package com.brewmarket.post;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class LocalPostImageStorageTest {
    @TempDir
    Path directory;

    @Test
    void savesAndDeletesRealImage() throws Exception {
        LocalPostImageStorage storage = new LocalPostImageStorage(directory.toString());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);

        String key = storage.save(new MockMultipartFile("image", "../../photo.png", "image/png", bytes.toByteArray()));

        assertTrue(Files.exists(storage.resolve(key)));
        assertEquals(directory.toAbsolutePath(), storage.resolve(key).getParent());
        assertNotNull(ImageIO.read(storage.resolve(key).toFile()));

        storage.delete(key);
        assertFalse(Files.exists(storage.resolve(key)));
    }

    @Test
    void rejectsFakeImageAndUnsafePath() {
        LocalPostImageStorage storage = new LocalPostImageStorage(directory.toString());

        assertThrows(ResponseStatusException.class, () -> storage.save(
                new MockMultipartFile("image", "fake.png", "image/png", "not an image".getBytes())));
        assertThrows(ResponseStatusException.class, () -> storage.resolve("../secret.png"));
    }
}
