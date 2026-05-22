package com.fleetops.core.module.outbox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleetops.core.module.outbox.model.OutboxEvent;
import com.fleetops.core.module.outbox.repository.OutboxEventRepository;
import com.fleetops.core.module.outbox.service.impl.OutboxServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxServiceImplTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OutboxServiceImpl outboxService;

    // ========================= save =========================

    @Test
    void save_savesOutboxEventWithCorrectTopic() throws Exception {
        Object payload = Map.of("key", "value");
        when(objectMapper.writeValueAsString(payload)).thenReturn("{\"key\":\"value\"}");
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        outboxService.save("my-topic", "MY_EVENT", payload);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getTopic()).isEqualTo("my-topic");
    }

    @Test
    void save_savesOutboxEventWithCorrectEventType() throws Exception {
        Object payload = Map.of("id", 1);
        when(objectMapper.writeValueAsString(payload)).thenReturn("{\"id\":1}");
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        outboxService.save("events-topic", "VEHICLE_REGISTERED", payload);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("VEHICLE_REGISTERED");
    }

    @Test
    void save_savesOutboxEventWithSerializedJsonPayload() throws Exception {
        Object payload = Map.of("vehicleId", 10);
        String json = "{\"vehicleId\":10}";
        when(objectMapper.writeValueAsString(payload)).thenReturn(json);
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        outboxService.save("topic", "EVENT", payload);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getPayload()).isEqualTo(json);
    }

    @Test
    void save_callsObjectMapperToSerializePayload() throws Exception {
        Object payload = Map.of("a", "b");
        when(objectMapper.writeValueAsString(payload)).thenReturn("{\"a\":\"b\"}");
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        outboxService.save("topic", "EVENT", payload);

        verify(objectMapper).writeValueAsString(payload);
    }

    @Test
    void save_throwsRuntimeExceptionOnSerializationFailure() throws Exception {
        Object payload = new Object();
        when(objectMapper.writeValueAsString(payload)).thenThrow(new JsonProcessingException("error") {});

        assertThatThrownBy(() -> outboxService.save("topic", "EVENT", payload))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void save_runtimeExceptionCarriesCorrectMessage() throws Exception {
        Object payload = new Object();
        when(objectMapper.writeValueAsString(payload)).thenThrow(new JsonProcessingException("error") {});

        assertThatThrownBy(() -> outboxService.save("topic", "EVENT", payload))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Outbox serialization failed");
    }

    @Test
    void save_wrapsJsonProcessingExceptionAsCause() throws Exception {
        Object payload = new Object();
        JsonProcessingException cause = new JsonProcessingException("serialization error") {};
        when(objectMapper.writeValueAsString(payload)).thenThrow(cause);

        assertThatThrownBy(() -> outboxService.save("topic", "EVENT", payload))
                .isInstanceOf(RuntimeException.class)
                .hasCause(cause);
    }

    @Test
    void save_doesNotCallRepositoryWhenSerializationFails() throws Exception {
        Object payload = new Object();
        when(objectMapper.writeValueAsString(payload)).thenThrow(new JsonProcessingException("error") {});

        assertThatThrownBy(() -> outboxService.save("topic", "EVENT", payload));

        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void save_callsRepositorySaveExactlyOnce() throws Exception {
        Object payload = Map.of("a", "b");
        when(objectMapper.writeValueAsString(payload)).thenReturn("{\"a\":\"b\"}");
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        outboxService.save("topic", "EVENT", payload);

        verify(outboxEventRepository, times(1)).save(any(OutboxEvent.class));
    }

    @Test
    void save_topicIsPreservedExactlyAsProvided() throws Exception {
        String topic = "fleet-ops.activity.events";
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        outboxService.save(topic, "EVENT", new Object());

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getTopic()).isEqualTo(topic);
    }

    @Test
    void save_eventTypeIsPreservedExactlyAsProvided() throws Exception {
        String eventType = "MAINTENANCE_FLAG_RESOLVED";
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        outboxService.save("topic", eventType, new Object());

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(eventType);
    }

    @Test
    void save_objectMapperCalledWithExactPayloadReference() throws Exception {
        Object payload = Map.of("ref", "check");
        when(objectMapper.writeValueAsString(payload)).thenReturn("{\"ref\":\"check\"}");
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        outboxService.save("topic", "EVENT", payload);

        verify(objectMapper).writeValueAsString(eq(payload));
    }

    @Test
    void save_savedEventHasAllRequiredFieldsNonNull() throws Exception {
        Object payload = Map.of("x", 1);
        when(objectMapper.writeValueAsString(payload)).thenReturn("{\"x\":1}");
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        outboxService.save("topic", "EVENT", payload);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertThat(saved.getTopic()).isNotNull();
        assertThat(saved.getEventType()).isNotNull();
        assertThat(saved.getPayload()).isNotNull();
    }

    @Test
    void save_doesNotThrowWhenSerializationSucceeds() throws Exception {
        Object payload = Map.of("ok", true);
        when(objectMapper.writeValueAsString(payload)).thenReturn("{\"ok\":true}");
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> outboxService.save("topic", "EVENT", payload))
                .doesNotThrowAnyException();
    }

    @Test
    void save_multipleSavesEachInvokeRepositoryOnce() throws Exception {
        Object payload1 = Map.of("type", "A");
        Object payload2 = Map.of("type", "B");
        when(objectMapper.writeValueAsString(payload1)).thenReturn("{\"type\":\"A\"}");
        when(objectMapper.writeValueAsString(payload2)).thenReturn("{\"type\":\"B\"}");
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        outboxService.save("topic", "E1", payload1);
        outboxService.save("topic", "E2", payload2);

        verify(outboxEventRepository, times(2)).save(any(OutboxEvent.class));
    }

    @Test
    void save_secondSaveStoresCorrectPayload() throws Exception {
        Object payload2 = Map.of("type", "B");
        when(objectMapper.writeValueAsString(payload2)).thenReturn("{\"type\":\"B\"}");
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        outboxService.save("topic", "E2", payload2);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getPayload()).isEqualTo("{\"type\":\"B\"}");
    }

    @Test
    void save_differentTopicsStoredCorrectly() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        outboxService.save("topic-a", "EVENT", new Object());

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getTopic()).isEqualTo("topic-a");
    }

    @Test
    void save_serializationExceptionTypeDoesNotMatterForWrapping() throws Exception {
        Object payload = new Object();
        when(objectMapper.writeValueAsString(payload)).thenThrow(new JsonProcessingException("custom message") {});

        assertThatThrownBy(() -> outboxService.save("topic", "EVENT", payload))
                .isInstanceOf(RuntimeException.class)
                .getCause()
                .isInstanceOf(JsonProcessingException.class)
                .hasMessageContaining("custom message");
    }
}
