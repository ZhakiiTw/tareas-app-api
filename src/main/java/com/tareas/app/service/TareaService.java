package com.tareas.app.service;

import com.tareas.app.dto.TareaActualizacionDTO;
import com.tareas.app.dto.TareaCreacionDTO;
import com.tareas.app.dto.TareaDTO;
import com.tareas.app.exception.ResourceNotFoundException;
import com.tareas.app.model.Tarea;
import com.tareas.app.model.TipoTarea;
import com.tareas.app.model.Usuario;
import com.tareas.app.repository.TareaRepository;
import com.tareas.app.repository.TipoTareaRepository;
import com.tareas.app.repository.UsuarioRepository;
import com.tareas.app.service.util.MapeadorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TareaService {

    private final TareaRepository tareaRepository;
    private final UsuarioRepository usuarioRepository;
    private final MapeadorService mapeadorService;
    private final TipoTareaRepository tipoTareaRepository;

    public List<TareaDTO> listarPorUsuario(String email) {
        log.info("=== LISTANDO TAREAS DEL USUARIO: {} ===", email);

        return tareaRepository.findByUsuarioEmailOrderByFechaAsc(email)
                .stream()
                .map(mapeadorService::toTareaDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public TareaDTO crearTarea(TareaCreacionDTO dto, String email) {
        log.info("=== CREANDO NUEVA TAREA ===");
        log.debug("Usuario autenticado: {}", email);

        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        TipoTarea tipoTarea = tipoTareaRepository.findByIdAndUsuarioEmail(dto.getTipoTareaId(), email)
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de tarea no encontrado para este usuario"));

        Tarea nuevaTarea = mapeadorService.toTareaEntity(dto, usuario, tipoTarea);
        Tarea tareaGuardada = tareaRepository.save(nuevaTarea);

        log.info("Tarea creada - ID: {}", tareaGuardada.getId());
        return mapeadorService.toTareaDTO(tareaGuardada);
    }

    @Transactional
    public TareaDTO actualizarTareaParcial(Long id, TareaActualizacionDTO dto, String email) {
        log.info("=== ACTUALIZANDO TAREA ID: {} ===", id);

        // Solo si la tarea pertenece al usuario
        Tarea tarea = tareaRepository.findByIdAndUsuarioEmail(id, email)
                .orElseThrow(() -> new ResourceNotFoundException("Tarea no encontrada para este usuario"));

        if (dto.getTitulo() != null) tarea.setTitulo(dto.getTitulo());
        if (dto.getDescripcion() != null) tarea.setDescripcion(dto.getDescripcion());
        if (dto.getFecha() != null) tarea.setFecha(dto.getFecha());
        if (dto.getCompletada() != null) tarea.setCompletada(dto.getCompletada());
        if (dto.getUrgencia() != null) tarea.setUrgencia(dto.getUrgencia());

        if (dto.getTipoTareaId() != null) {
            TipoTarea tipoTarea = tipoTareaRepository.findByIdAndUsuarioEmail(dto.getTipoTareaId(), email)
                    .orElseThrow(() -> new ResourceNotFoundException("Tipo de tarea no encontrado para este usuario"));
            tarea.setTipoTarea(tipoTarea);
        }

        Tarea tareaActualizada = tareaRepository.save(tarea);
        log.info("Tarea actualizada - ID: {}", tareaActualizada.getId());

        return mapeadorService.toTareaDTO(tareaActualizada);
    }

    @Transactional
    public void eliminarTarea(Long id, String email) {
        log.info("=== ELIMINANDO TAREA ID: {} ===", id);

        if (!tareaRepository.existsByIdAndUsuarioEmail(id, email)) {
            throw new ResourceNotFoundException("Tarea no encontrada para este usuario");
        }

        tareaRepository.deleteByIdAndUsuarioEmail(id, email);
        log.info("Tarea eliminada - ID: {}", id);
    }
}