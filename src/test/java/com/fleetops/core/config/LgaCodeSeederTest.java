package com.fleetops.core.config;

import com.fleetops.core.vehicle.entity.LgaCode;
import com.fleetops.core.vehicle.repository.LgaCodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LgaCodeSeederTest {

    @Mock private LgaCodeRepository lgaCodeRepository;
    @InjectMocks private LgaCodeSeeder lgaCodeSeeder;

    @Test
    void run_whenTableEmpty_seedsFromCsv() throws Exception {
        when(lgaCodeRepository.count()).thenReturn(0L);
        when(lgaCodeRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        lgaCodeSeeder.run(null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LgaCode>> captor = ArgumentCaptor.forClass(List.class);
        verify(lgaCodeRepository).saveAll(captor.capture());

        List<LgaCode> saved = captor.getValue();
        assertThat(saved).isNotEmpty();
        // all codes must be 3-character uppercase strings
        assertThat(saved).allMatch(c -> c.getCode().length() == 3);
        assertThat(saved).allMatch(c -> c.getCode().equals(c.getCode().toUpperCase()));
        // spot-check known entries
        assertThat(saved).anyMatch(c -> c.getCode().equals("KJA") && c.getState().equals("Lagos"));
        assertThat(saved).anyMatch(c -> c.getCode().equals("ABJ") && c.getState().equals("FCT"));
        assertThat(saved).anyMatch(c -> c.getCode().equals("PHC") && c.getState().equals("Rivers"));
    }

    @Test
    void run_whenTableAlreadySeeded_skipsImport() throws Exception {
        when(lgaCodeRepository.count()).thenReturn(10L);

        lgaCodeSeeder.run(null);

        verify(lgaCodeRepository, never()).saveAll(any());
    }
}
