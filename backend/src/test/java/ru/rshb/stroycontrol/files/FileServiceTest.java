package ru.rshb.stroycontrol.files;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ru.rshb.stroycontrol.common.ApiException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

class FileServiceTest {
    final FileTransactions transactions = mock(FileTransactions.class);
    final ObjectStorage storage = mock(ObjectStorage.class);
    final FileService service = new FileService(transactions, storage);

    @Test
    @DisplayName("Неизвестный исход PUT сохраняет попытку; повтор восстанавливает объект по хешу")
    void recoverUnknownPut() {
        var actor = UUID.randomUUID();
        var project = UUID.randomUUID();
        var stage = UUID.randomUUID();
        var key = UUID.randomUUID();
        var id = UUID.randomUUID();
        var scope = new UploadScope(actor, project, stage, "JOURNAL", key);
        var bytes = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF".getBytes(StandardCharsets.UTF_8);
        var attempt = new FileTransactions.Attempt(id, "private/object", "UPLOADING", null);
        when(transactions.prepare(any(), any())).thenReturn(attempt);
        doThrow(new IllegalStateException("timeout after put"))
                .when(storage)
                .put(anyString(), anyString(), anyString(), any());
        assertThatThrownBy(() -> service.upload(scope, "passport.pdf", "", bytes))
                .isInstanceOf(ApiException.class);
        verify(transactions).failed(id);
        verify(storage, never()).delete(anyString());
        when(storage.matches("private/object", FileInput.sha256(bytes))).thenReturn(true);
        service.upload(scope, "passport.pdf", "", bytes);
        verify(transactions).complete(actor, project, id);
    }

    @Test
    @DisplayName("Удержанное подтверждение не удаляется уборкой временных объектов")
    void cleanupSkipsRetained() {
        var id = UUID.randomUUID();
        when(transactions.candidates()).thenReturn(List.of(id));
        when(transactions.claimCleanup(id)).thenReturn(null);
        service.cleanup();
        verify(storage, never()).delete(anyString());
        verify(transactions, never()).deleted(any());
    }

    @Test
    @DisplayName("Ошибка удаления не отмечает объект удалённым и допускает следующий проход")
    void retryCleanup() {
        var id = UUID.randomUUID();
        when(transactions.candidates()).thenReturn(List.of(id));
        when(transactions.claimCleanup(id)).thenReturn("private/temp");
        doThrow(new IllegalStateException("storage offline")).when(storage).delete("private/temp");
        service.cleanup();
        verify(transactions, never()).deleted(id);
    }
}
