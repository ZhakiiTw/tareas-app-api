package com.tareas.app.service;

import com.tareas.app.dto.TareaActualizacionDTO;
import com.tareas.app.dto.TareaDTO;
import com.tareas.app.exception.ResourceNotFoundException;
import com.tareas.app.exception.ValidacionException;
import com.tareas.app.model.Tarea;
import com.tareas.app.model.TipoTarea;
import com.tareas.app.model.Usuario;
import com.tareas.app.repository.TareaRepository;
import com.tareas.app.repository.TipoTareaRepository;
import com.tareas.app.repository.UsuarioRepository;
import com.tareas.app.service.util.MapeadorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TareaServiceTest {

    private static final String EMAIL = "usuario@test.com";
    private static final Long TAREA_ID = 1L;
    private static final LocalDate FECHA_PASADA = LocalDate.now().minusDays(1);

    @Mock
    private TareaRepository tareaRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private TipoTareaRepository tipoTareaRepository;

    private TareaService tareaService;
    private Tarea tareaVencida;

    @BeforeEach
    void setUp() {
        tareaService = new TareaService(tareaRepository, usuarioRepository, new MapeadorService(), tipoTareaRepository);

        Usuario usuario = Usuario.builder()
                .id(1L)
                .username("usuario")
                .email(EMAIL)
                .password("password")
                .build();
        TipoTarea tipoTarea = TipoTarea.builder()
                .id(1L)
                .nombre("Personal")
                .color("#4F46E5")
                .usuario(usuario)
                .build();
        tareaVencida = Tarea.builder()
                .id(TAREA_ID)
                .titulo("Tarea antigua")
                .descripcion("Descripción original")
                .fecha(FECHA_PASADA)
                .completada(false)
                .urgencia(1)
                .usuario(usuario)
                .tipoTarea(tipoTarea)
                .build();
    }

    private void mockGuardado() {
        when(tareaRepository.save(any(Tarea.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Editar el título de una tarea vencida sin cambiar la fecha es permitido")
    void editarTituloDeTareaVencidaSinCambiarFechaEsPermitido() {
        when(tareaRepository.findByIdAndUsuarioEmail(TAREA_ID, EMAIL)).thenReturn(Optional.of(tareaVencida));
        mockGuardado();

        TareaActualizacionDTO dto = new TareaActualizacionDTO();
        dto.setTitulo("Nuevo título");

        TareaDTO resultado = tareaService.actualizarTareaParcial(TAREA_ID, dto, EMAIL);

        assertThat(resultado.getTitulo()).isEqualTo("Nuevo título");
        assertThat(resultado.getFecha()).isEqualTo(FECHA_PASADA);
    }

    @Test
    @DisplayName("Editar la urgencia de una tarea vencida sin cambiar la fecha es permitido")
    void editarUrgenciaDeTareaVencidaSinCambiarFechaEsPermitido() {
        when(tareaRepository.findByIdAndUsuarioEmail(TAREA_ID, EMAIL)).thenReturn(Optional.of(tareaVencida));
        mockGuardado();

        TareaActualizacionDTO dto = new TareaActualizacionDTO();
        dto.setUrgencia(2);

        TareaDTO resultado = tareaService.actualizarTareaParcial(TAREA_ID, dto, EMAIL);

        assertThat(resultado.getUrgencia()).isEqualTo(2);
        assertThat(resultado.getFecha()).isEqualTo(FECHA_PASADA);
    }

    @Test
    @DisplayName("Conservar la fecha antigua existente no produce error")
    void conservarFechaAntiguaExistenteEsPermitido() {
        when(tareaRepository.findByIdAndUsuarioEmail(TAREA_ID, EMAIL)).thenReturn(Optional.of(tareaVencida));
        mockGuardado();

        TareaActualizacionDTO dto = new TareaActualizacionDTO();
        dto.setFecha(FECHA_PASADA);

        TareaDTO resultado = tareaService.actualizarTareaParcial(TAREA_ID, dto, EMAIL);

        assertThat(resultado.getFecha()).isEqualTo(FECHA_PASADA);
    }

    @Test
    @DisplayName("Cambiar la fecha por una fecha pasada diferente es rechazado")
    void cambiarFechaPorUnaPasadaDiferenteEsRechazado() {
        when(tareaRepository.findByIdAndUsuarioEmail(TAREA_ID, EMAIL)).thenReturn(Optional.of(tareaVencida));

        TareaActualizacionDTO dto = new TareaActualizacionDTO();
        dto.setFecha(LocalDate.now().minusDays(5));

        assertThatThrownBy(() -> tareaService.actualizarTareaParcial(TAREA_ID, dto, EMAIL))
                .isInstanceOf(ValidacionException.class)
                .hasMessage("La nueva fecha debe ser futura o presente");

        verify(tareaRepository, never()).save(any(Tarea.class));
    }

    @Test
    @DisplayName("Cambiar la fecha por hoy es permitido")
    void cambiarFechaPorHoyEsPermitido() {
        when(tareaRepository.findByIdAndUsuarioEmail(TAREA_ID, EMAIL)).thenReturn(Optional.of(tareaVencida));
        mockGuardado();

        TareaActualizacionDTO dto = new TareaActualizacionDTO();
        dto.setFecha(LocalDate.now());

        TareaDTO resultado = tareaService.actualizarTareaParcial(TAREA_ID, dto, EMAIL);

        assertThat(resultado.getFecha()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("Cambiar la fecha por una fecha futura es permitido")
    void cambiarFechaPorUnaFuturaEsPermitido() {
        when(tareaRepository.findByIdAndUsuarioEmail(TAREA_ID, EMAIL)).thenReturn(Optional.of(tareaVencida));
        mockGuardado();

        TareaActualizacionDTO dto = new TareaActualizacionDTO();
        dto.setFecha(LocalDate.now().plusDays(2));

        TareaDTO resultado = tareaService.actualizarTareaParcial(TAREA_ID, dto, EMAIL);

        assertThat(resultado.getFecha()).isEqualTo(LocalDate.now().plusDays(2));
    }

    @Test
    @DisplayName("Completar una tarea vencida es permitido")
    void completarTareaVencidaEsPermitido() {
        when(tareaRepository.findByIdAndUsuarioEmail(TAREA_ID, EMAIL)).thenReturn(Optional.of(tareaVencida));
        mockGuardado();

        TareaDTO resultado = tareaService.completarTarea(TAREA_ID, EMAIL);

        assertThat(resultado.getCompletada()).isTrue();
        assertThat(resultado.getFecha()).isEqualTo(FECHA_PASADA);
    }

    @Test
    @DisplayName("Reabrir una tarea vencida es permitido")
    void reabrirTareaVencidaEsPermitido() {
        tareaVencida.setCompletada(true);
        when(tareaRepository.findByIdAndUsuarioEmail(TAREA_ID, EMAIL)).thenReturn(Optional.of(tareaVencida));
        mockGuardado();

        TareaDTO resultado = tareaService.reabrirTarea(TAREA_ID, EMAIL);

        assertThat(resultado.getCompletada()).isFalse();
        assertThat(resultado.getFecha()).isEqualTo(FECHA_PASADA);
    }

    @Test
    @DisplayName("Editar una tarea de otro usuario es rechazado")
    void editarTareaDeOtroUsuarioEsRechazado() {
        when(tareaRepository.findByIdAndUsuarioEmail(TAREA_ID, EMAIL)).thenReturn(Optional.empty());

        TareaActualizacionDTO dto = new TareaActualizacionDTO();
        dto.setTitulo("Titulo ajeno");

        assertThatThrownBy(() -> tareaService.actualizarTareaParcial(TAREA_ID, dto, EMAIL))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Tarea no encontrada para este usuario");
    }

    @Test
    @DisplayName("Completar una tarea de otro usuario es rechazado")
    void completarTareaDeOtroUsuarioEsRechazado() {
        when(tareaRepository.findByIdAndUsuarioEmail(TAREA_ID, EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tareaService.completarTarea(TAREA_ID, EMAIL))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Tarea no encontrada para este usuario");
    }

    @Test
    @DisplayName("Reabrir una tarea de otro usuario es rechazado")
    void reabrirTareaDeOtroUsuarioEsRechazado() {
        when(tareaRepository.findByIdAndUsuarioEmail(TAREA_ID, EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tareaService.reabrirTarea(TAREA_ID, EMAIL))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Tarea no encontrada para este usuario");
    }
}
