package com.itq.document.service;

import com.itq.document.dto.*;
import com.itq.document.exception.DocumentNotFoundException;
import com.itq.document.model.*;
import com.itq.document.repository.DocumentRepository;
import com.itq.document.repository.HistoryRepository;
import com.itq.document.repository.RegistryRepository;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentServiceImpl Unit Tests")
class DocumentServiceImplTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private HistoryRepository historyRepository;

    @Mock
    private RegistryRepository registryRepository;

    @Mock
    private DocumentNumberGenerator numberGenerator;

    @InjectMocks
    private DocumentServiceImpl documentService;

    @Captor
    private ArgumentCaptor<Document> documentCaptor;

    @Captor
    private ArgumentCaptor<History> historyCaptor;

    @Captor
    private ArgumentCaptor<RegistryEntry> registryCaptor;

    private Document testDocument;
    private final Long TEST_DOC_ID = 1L;
    private final String TEST_DOC_NUMBER = "DOC-TEST-001";
    private final String TEST_AUTHOR = "Тестовый Автор";
    private final String TEST_TITLE = "Тестовый документ";
    private final String TEST_INITIATOR = "Иванов И.И.";
    private final String TEST_APPROVER = "Петров П.П.";
    private final String TEST_COMMENT = "Тестовый комментарий";

    @BeforeEach
    void setUp() {
        testDocument = new Document();
        testDocument.setId(TEST_DOC_ID);
        testDocument.setDocumentNumber(TEST_DOC_NUMBER);
        testDocument.setAuthor(TEST_AUTHOR);
        testDocument.setTitle(TEST_TITLE);
        testDocument.setStatus(DocumentStatus.DRAFT);
        testDocument.setCreatedAt(LocalDateTime.now());
        testDocument.setUpdatedAt(LocalDateTime.now());
    }

    // ========================================================================
    // 1. HAPPY PATH - Один документ
    // ========================================================================

    @Test
    @DisplayName("Happy Path: Создание документа")
    void createDocument_Success() {
        // Given
        CreateDocumentRequest request = new CreateDocumentRequest();
        request.setAuthor(TEST_AUTHOR);
        request.setTitle(TEST_TITLE);

        when(numberGenerator.generate()).thenReturn(TEST_DOC_NUMBER);
        when(documentRepository.save(any(Document.class))).thenReturn(testDocument);

        // When
        DocumentDto result = documentService.createDocument(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(TEST_DOC_ID);
        assertThat(result.getDocumentNumber()).isEqualTo(TEST_DOC_NUMBER);
        assertThat(result.getAuthor()).isEqualTo(TEST_AUTHOR);
        assertThat(result.getTitle()).isEqualTo(TEST_TITLE);
        assertThat(result.getStatus()).isEqualTo(DocumentStatus.DRAFT.name());

        verify(documentRepository).save(documentCaptor.capture());
        Document savedDoc = documentCaptor.getValue();
        assertThat(savedDoc.getDocumentNumber()).isEqualTo(TEST_DOC_NUMBER);
        assertThat(savedDoc.getAuthor()).isEqualTo(TEST_AUTHOR);
        assertThat(savedDoc.getTitle()).isEqualTo(TEST_TITLE);
        assertThat(savedDoc.getStatus()).isEqualTo(DocumentStatus.DRAFT);
    }

    @Test
    @DisplayName("Happy Path: Получение документа с историей")
    void getDocumentWithHistory_Success() {
        // Given
        History history1 = new History();
        history1.setId(1L);
        history1.setDocument(testDocument);
        history1.setInitiator(TEST_INITIATOR);
        history1.setAction(DocumentAction.SUBMIT);
        history1.setCreatedAt(LocalDateTime.now());

        History history2 = new History();
        history2.setId(2L);
        history2.setDocument(testDocument);
        history2.setInitiator(TEST_APPROVER);
        history2.setAction(DocumentAction.APPROVE);
        history2.setCreatedAt(LocalDateTime.now());

        when(documentRepository.findById(TEST_DOC_ID)).thenReturn(Optional.of(testDocument));
        when(historyRepository.findByDocumentIdOrderByCreatedAtDesc(TEST_DOC_ID))
                .thenReturn(List.of(history2, history1));

        // When
        DocumentWithHistoryDto result = documentService.getDocumentWithHistory(TEST_DOC_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getDocument().getId()).isEqualTo(TEST_DOC_ID);
        assertThat(result.getHistory()).hasSize(2);
        assertThat(result.getHistory().get(0).getAction()).isEqualTo("APPROVE");
        assertThat(result.getHistory().get(1).getAction()).isEqualTo("SUBMIT");
    }

    @Test
    @DisplayName("Получение несуществующего документа - выбрасывает исключение")
    void getDocumentWithHistory_NotFound_ThrowsException() {
        // Given
        when(documentRepository.findById(999L)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> documentService.getDocumentWithHistory(999L))
                .isInstanceOf(DocumentNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("Happy Path: Получение батча документов")
    void getDocumentsBatch_Success() {
        // Given
        Document doc2 = new Document();
        doc2.setId(2L);
        doc2.setDocumentNumber("DOC-002");
        doc2.setAuthor("Автор 2");
        doc2.setTitle("Документ 2");
        doc2.setStatus(DocumentStatus.SUBMITTED);

        List<Long> ids = List.of(1L, 2L);
        when(documentRepository.findAllById(ids)).thenReturn(List.of(testDocument, doc2));

        // When
        List<DocumentDto> result = documentService.getDocumentsBatch(ids, Pageable.unpaged());

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(1).getId()).isEqualTo(2L);
        assertThat(result.get(1).getStatus()).isEqualTo(DocumentStatus.SUBMITTED.name());
    }

    // ========================================================================
    // 2. SUBMIT TESTS
    // ========================================================================

    @Test
    @DisplayName("Submit: Успешная отправка одного документа")
    void submitDocuments_SingleSuccess() {
        // Given
        BatchOperationRequest request = new BatchOperationRequest();
        request.setIds(List.of(TEST_DOC_ID));
        request.setInitiator(TEST_INITIATOR);
        request.setComment(TEST_COMMENT);

        when(documentRepository.findById(TEST_DOC_ID)).thenReturn(Optional.of(testDocument));
        when(documentRepository.save(any(Document.class))).thenReturn(testDocument);
        when(historyRepository.save(any(History.class))).thenReturn(new History());

        // When
        List<OperationResult> results = documentService.submitDocuments(request);

        // Then
        assertThat(results).hasSize(1);
        OperationResult result = results.get(0);
        assertThat(result.getDocumentId()).isEqualTo(TEST_DOC_ID);
        assertThat(result.getStatus()).isEqualTo(OperationResult.OperationStatus.SUCCESS);
        assertThat(result.getMessage()).contains("successfully");

        verify(documentRepository).save(documentCaptor.capture());
        assertThat(documentCaptor.getValue().getStatus()).isEqualTo(DocumentStatus.SUBMITTED);

        verify(historyRepository).save(historyCaptor.capture());
        History savedHistory = historyCaptor.getValue();
        assertThat(savedHistory.getDocument().getId()).isEqualTo(TEST_DOC_ID);
        assertThat(savedHistory.getInitiator()).isEqualTo(TEST_INITIATOR);
        assertThat(savedHistory.getAction()).isEqualTo(DocumentAction.SUBMIT);
        assertThat(savedHistory.getComment()).isEqualTo(TEST_COMMENT);
    }

    @Test
    @DisplayName("Submit: Попытка отправить уже отправленный документ")
    void submitDocuments_AlreadySubmitted_ReturnsConflict() {
        // Given
        testDocument.setStatus(DocumentStatus.SUBMITTED);

        BatchOperationRequest request = new BatchOperationRequest();
        request.setIds(List.of(TEST_DOC_ID));
        request.setInitiator(TEST_INITIATOR);

        when(documentRepository.findById(TEST_DOC_ID)).thenReturn(Optional.of(testDocument));

        // When
        List<OperationResult> results = documentService.submitDocuments(request);

        // Then
        assertThat(results).hasSize(1);
        OperationResult result = results.get(0);
        assertThat(result.getDocumentId()).isEqualTo(TEST_DOC_ID);
        assertThat(result.getStatus()).isEqualTo(OperationResult.OperationStatus.CONFLICT);
        assertThat(result.getMessage()).contains("expected DRAFT");

        verify(documentRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
    }

    @Test
    @DisplayName("Submit: Попытка отправить утвержденный документ")
    void submitDocuments_AlreadyApproved_ReturnsConflict() {
        // Given
        testDocument.setStatus(DocumentStatus.APPROVED);

        BatchOperationRequest request = new BatchOperationRequest();
        request.setIds(List.of(TEST_DOC_ID));
        request.setInitiator(TEST_INITIATOR);

        when(documentRepository.findById(TEST_DOC_ID)).thenReturn(Optional.of(testDocument));

        // When
        List<OperationResult> results = documentService.submitDocuments(request);

        // Then
        assertThat(results).hasSize(1);
        OperationResult result = results.get(0);
        assertThat(result.getStatus()).isEqualTo(OperationResult.OperationStatus.CONFLICT);
        assertThat(result.getMessage()).contains("expected DRAFT");
    }

    @Test
    @DisplayName("Submit: Попытка отправить несуществующий документ")
    void submitDocuments_NotFound_ReturnsNotFound() {
        // Given
        BatchOperationRequest request = new BatchOperationRequest();
        request.setIds(List.of(999L));
        request.setInitiator(TEST_INITIATOR);

        when(documentRepository.findById(999L)).thenReturn(Optional.empty());

        // When
        List<OperationResult> results = documentService.submitDocuments(request);

        // Then
        assertThat(results).hasSize(1);
        OperationResult result = results.get(0);
        assertThat(result.getDocumentId()).isEqualTo(999L);
        assertThat(result.getStatus()).isEqualTo(OperationResult.OperationStatus.NOT_FOUND);
        assertThat(result.getMessage()).contains("not found");
    }

    @Test
    @DisplayName("Submit: Пакетная отправка с разными результатами")
    void submitDocuments_BatchWithMixedResults() {
        // Given
        Document doc1 = testDocument; // DRAFT - success

        Document doc2 = new Document();
        doc2.setId(2L);
        doc2.setStatus(DocumentStatus.SUBMITTED); // already submitted - conflict

        Document doc3 = new Document();
        doc3.setId(3L);
        doc3.setStatus(DocumentStatus.DRAFT); // DRAFT - success

        BatchOperationRequest request = new BatchOperationRequest();
        request.setIds(List.of(1L, 2L, 3L, 999L));
        request.setInitiator(TEST_INITIATOR);

        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc1));
        when(documentRepository.findById(2L)).thenReturn(Optional.of(doc2));
        when(documentRepository.findById(3L)).thenReturn(Optional.of(doc3));
        when(documentRepository.findById(999L)).thenReturn(Optional.empty());

        // When
        List<OperationResult> results = documentService.submitDocuments(request);

        // Then
        assertThat(results).hasSize(4);

        // Проверяем результаты
        assertThat(results.get(0).getDocumentId()).isEqualTo(1L);
        assertThat(results.get(0).getStatus()).isEqualTo(OperationResult.OperationStatus.SUCCESS);

        assertThat(results.get(1).getDocumentId()).isEqualTo(2L);
        assertThat(results.get(1).getStatus()).isEqualTo(OperationResult.OperationStatus.CONFLICT);

        assertThat(results.get(2).getDocumentId()).isEqualTo(3L);
        assertThat(results.get(2).getStatus()).isEqualTo(OperationResult.OperationStatus.SUCCESS);

        assertThat(results.get(3).getDocumentId()).isEqualTo(999L);
        assertThat(results.get(3).getStatus()).isEqualTo(OperationResult.OperationStatus.NOT_FOUND);

        verify(documentRepository, times(2)).save(any(Document.class));
        verify(historyRepository, times(2)).save(any(History.class));
    }

    // ========================================================================
    // 3. APPROVE TESTS
    // ========================================================================

    @Test
    @DisplayName("Approve: Успешное утверждение документа")
    void approveDocuments_SingleSuccess() {
        // Given
        testDocument.setStatus(DocumentStatus.SUBMITTED);

        BatchOperationRequest request = new BatchOperationRequest();
        request.setIds(List.of(TEST_DOC_ID));
        request.setInitiator(TEST_APPROVER);
        request.setComment(TEST_COMMENT);

        when(documentRepository.findById(TEST_DOC_ID)).thenReturn(Optional.of(testDocument));
        when(registryRepository.save(any(RegistryEntry.class))).thenReturn(new RegistryEntry());
        when(documentRepository.save(any(Document.class))).thenReturn(testDocument);
        when(historyRepository.save(any(History.class))).thenReturn(new History());

        // When
        List<OperationResult> results = documentService.approveDocuments(request);

        // Then
        assertThat(results).hasSize(1);
        OperationResult result = results.get(0);
        assertThat(result.getDocumentId()).isEqualTo(TEST_DOC_ID);
        assertThat(result.getStatus()).isEqualTo(OperationResult.OperationStatus.SUCCESS);

        // Проверяем порядок операций: сначала registry, потом document, потом history
        verify(registryRepository).save(registryCaptor.capture());
        RegistryEntry savedRegistry = registryCaptor.getValue();
        assertThat(savedRegistry.getDocumentId()).isEqualTo(TEST_DOC_ID);
        assertThat(savedRegistry.getApprovedBy()).isEqualTo(TEST_APPROVER);

        verify(documentRepository).save(documentCaptor.capture());
        assertThat(documentCaptor.getValue().getStatus()).isEqualTo(DocumentStatus.APPROVED);

        verify(historyRepository).save(historyCaptor.capture());
        History savedHistory = historyCaptor.getValue();
        assertThat(savedHistory.getDocument().getId()).isEqualTo(TEST_DOC_ID);
        assertThat(savedHistory.getInitiator()).isEqualTo(TEST_APPROVER);
        assertThat(savedHistory.getAction()).isEqualTo(DocumentAction.APPROVE);
    }

    @Test
    @DisplayName("Approve: Попытка утвердить черновик")
    void approveDocuments_DraftDocument_ReturnsConflict() {
        // Given - документ в статусе DRAFT
        BatchOperationRequest request = new BatchOperationRequest();
        request.setIds(List.of(TEST_DOC_ID));
        request.setInitiator(TEST_APPROVER);

        when(documentRepository.findById(TEST_DOC_ID)).thenReturn(Optional.of(testDocument));

        // When
        List<OperationResult> results = documentService.approveDocuments(request);

        // Then
        assertThat(results).hasSize(1);
        OperationResult result = results.get(0);
        assertThat(result.getStatus()).isEqualTo(OperationResult.OperationStatus.CONFLICT);
        assertThat(result.getMessage()).contains("expected SUBMITTED");

        verify(registryRepository, never()).save(any());
        verify(documentRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
    }

    @Test
    @DisplayName("Approve: Попытка утвердить уже утвержденный документ")
    void approveDocuments_AlreadyApproved_ReturnsConflict() {
        // Given
        testDocument.setStatus(DocumentStatus.APPROVED);

        BatchOperationRequest request = new BatchOperationRequest();
        request.setIds(List.of(TEST_DOC_ID));
        request.setInitiator(TEST_APPROVER);

        when(documentRepository.findById(TEST_DOC_ID)).thenReturn(Optional.of(testDocument));

        // When
        List<OperationResult> results = documentService.approveDocuments(request);

        // Then
        assertThat(results).hasSize(1);
        OperationResult result = results.get(0);
        assertThat(result.getStatus()).isEqualTo(OperationResult.OperationStatus.CONFLICT);
        assertThat(result.getMessage()).contains("expected SUBMITTED");
    }

    @Test
    @DisplayName("Approve: Ошибка при сохранении в регистр")
    void approveDocuments_RegistryError_ReturnsRegistryError() {
        // Given
        testDocument.setStatus(DocumentStatus.SUBMITTED);

        BatchOperationRequest request = new BatchOperationRequest();
        request.setIds(List.of(TEST_DOC_ID));
        request.setInitiator(TEST_APPROVER);

        when(documentRepository.findById(TEST_DOC_ID)).thenReturn(Optional.of(testDocument));
        when(registryRepository.save(any(RegistryEntry.class)))
                .thenThrow(new RuntimeException("Database error"));

        // When
        List<OperationResult> results = documentService.approveDocuments(request);

        // Then
        assertThat(results).hasSize(1);
        OperationResult result = results.get(0);
        assertThat(result.getDocumentId()).isEqualTo(TEST_DOC_ID);
        assertThat(result.getStatus()).isEqualTo(OperationResult.OperationStatus.REGISTRY_ERROR);
        assertThat(result.getMessage()).contains("Failed to register");

        // Проверяем, что документ НЕ был обновлен
        verify(documentRepository, never()).save(any(Document.class));
        verify(historyRepository, never()).save(any(History.class));
    }

    @Test
    @DisplayName("Approve: Пакетное утверждение с частичными результатами")
    void approveDocuments_BatchWithMixedResults() {
        // Given
        Document doc1 = testDocument;
        doc1.setStatus(DocumentStatus.SUBMITTED); // success

        Document doc2 = new Document();
        doc2.setId(2L);
        doc2.setStatus(DocumentStatus.DRAFT); // conflict - wrong status

        Document doc3 = new Document();
        doc3.setId(3L);
        doc3.setStatus(DocumentStatus.SUBMITTED); // success

        BatchOperationRequest request = new BatchOperationRequest();
        request.setIds(List.of(1L, 2L, 3L, 999L));
        request.setInitiator(TEST_APPROVER);

        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc1));
        when(documentRepository.findById(2L)).thenReturn(Optional.of(doc2));
        when(documentRepository.findById(3L)).thenReturn(Optional.of(doc3));
        when(documentRepository.findById(999L)).thenReturn(Optional.empty());

        // When
        List<OperationResult> results = documentService.approveDocuments(request);

        // Then
        assertThat(results).hasSize(4);

        assertThat(results.get(0).getDocumentId()).isEqualTo(1L);
        assertThat(results.get(0).getStatus()).isEqualTo(OperationResult.OperationStatus.SUCCESS);

        assertThat(results.get(1).getDocumentId()).isEqualTo(2L);
        assertThat(results.get(1).getStatus()).isEqualTo(OperationResult.OperationStatus.CONFLICT);

        assertThat(results.get(2).getDocumentId()).isEqualTo(3L);
        assertThat(results.get(2).getStatus()).isEqualTo(OperationResult.OperationStatus.SUCCESS);

        assertThat(results.get(3).getDocumentId()).isEqualTo(999L);
        assertThat(results.get(3).getStatus()).isEqualTo(OperationResult.OperationStatus.NOT_FOUND);

        verify(registryRepository, times(2)).save(any(RegistryEntry.class));
        verify(documentRepository, times(2)).save(any(Document.class));
        verify(historyRepository, times(2)).save(any(History.class));
    }

    // ========================================================================
    // 4. SEARCH TESTS
    // ========================================================================

    @Test
    @DisplayName("Search: Поиск по дате создания")
    void searchDocuments_ByCreatedAt() {
        // Given
        DocumentSearchCriteria criteria = DocumentSearchCriteria.builder()
                .status(DocumentStatus.SUBMITTED.name())
                .author(TEST_AUTHOR)
                .dateFrom(LocalDateTime.now().minusDays(7))
                .dateTo(LocalDateTime.now())
                .searchByCreatedAt(true)
                .build();

        Pageable pageable = PageRequest.of(0, 10);
        Page<Document> page = new PageImpl<>(List.of(testDocument));

        when(documentRepository.searchByCreatedAt(
                criteria.getStatus(),
                criteria.getAuthor(),
                criteria.getDateFrom(),
                criteria.getDateTo(),
                pageable
        )).thenReturn(page);

        // When
        Page<DocumentDto> result = documentService.searchDocuments(criteria, pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(TEST_DOC_ID);
    }

    @Test
    @DisplayName("Search: Поиск по дате обновления")
    void searchDocuments_ByUpdatedAt() {
        // Given
        DocumentSearchCriteria criteria = DocumentSearchCriteria.builder()
                .status(DocumentStatus.APPROVED.name())
                .author(TEST_AUTHOR)
                .dateFrom(LocalDateTime.now().minusDays(30))
                .dateTo(LocalDateTime.now())
                .searchByCreatedAt(false)
                .build();

        Pageable pageable = PageRequest.of(0, 10);
        Page<Document> page = new PageImpl<>(List.of(testDocument));

        when(documentRepository.searchByUpdatedAt(
                criteria.getStatus(),
                criteria.getAuthor(),
                criteria.getDateFrom(),
                criteria.getDateTo(),
                pageable
        )).thenReturn(page);

        // When
        Page<DocumentDto> result = documentService.searchDocuments(criteria, pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(DocumentStatus.DRAFT.name());
    }

    // ========================================================================
    // 5. CONCURRENCY TEST
    // ========================================================================

    @Test
    @DisplayName("Concurrency: Тест конкурентного утверждения")
    void testConcurrentApproval() throws InterruptedException {
        // Given
        ConcurrencyTestRequest request = new ConcurrencyTestRequest();
        request.setDocumentId(TEST_DOC_ID);
        request.setThreads(5);
        request.setAttempts(20);
        request.setInitiator(TEST_APPROVER);

        testDocument.setStatus(DocumentStatus.SUBMITTED);

        // Атомарный счетчик успешных утверждений
        AtomicInteger successCount = new AtomicInteger(0);

        when(documentRepository.findById(TEST_DOC_ID)).thenReturn(Optional.of(testDocument));

        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> {
            Document doc = invocation.getArgument(0);

            // Только первый вызов успешен
            if (successCount.incrementAndGet() == 1) {
                doc.setStatus(DocumentStatus.APPROVED);
                return doc;
            } else {
                throw new OptimisticLockException("Concurrent modification");
            }
        });

        // When
        ConcurrencyTestResult result = documentService.testConcurrentApproval(request);

        // Then
        assertThat(result.getSuccessfulAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("Concurrency: Документ не найден")
    void testConcurrentApproval_DocumentNotFound() {
        // Given
        ConcurrencyTestRequest request = new ConcurrencyTestRequest();
        request.setDocumentId(999L);
        request.setThreads(2);
        request.setAttempts(5);
        request.setInitiator(TEST_APPROVER);

        when(documentRepository.findById(999L)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> documentService.testConcurrentApproval(request))
                .isInstanceOf(DocumentNotFoundException.class)
                .hasMessageContaining("999");
    }
}