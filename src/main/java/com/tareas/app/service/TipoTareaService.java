package com.tareas.app.service;

import com.tareas.app.dto.TipoTareaActualizacionDTO;
import com.tareas.app.dto.TipoTareaCreacionDTO;
import com.tareas.app.dto.TipoTareaDTO;
import com.tareas.app.exception.ResourceConflictException;
import com.tareas.app.exception.ResourceNotFoundException;
import com.tareas.app.model.TipoTarea;
import com.tareas.app.model.Usuario;
import com.tareas.app.repository.TareaRepository;
import com.tareas.app.repository.TipoTareaRepository;
import com.tareas.app.repository.UsuarioRepository;
import com.tareas.app.service.util.MapeadorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TipoTareaService {

    private final TipoTareaRepository tipoTareaRepository;
    private final UsuarioRepository usuarioRepository;
    private final TareaRepository tareaRepository;
    private final MapeadorService mapeadorService;

    public List<TipoTareaDTO> listarPorUsuario(String email) {
        return tipoTareaRepository.findByUsuarioEmailOrderByNombreAsc(email)
                .stream()
                .map(mapeadorService::toTipoTareaDTO)
                .toList();
    }

    @Transactional
    public TipoTareaDTO crearTipo(TipoTareaCreacionDTO dto, String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        if (tipoTareaRepository.existsByNombreIgnoreCaseAndUsuarioEmail(dto.getNombre(), email)) {
            throw new ResourceConflictException("Ya existe un tipo de tarea con ese nombre");
        }

        TipoTarea tipoTarea = TipoTarea.builder()
                .nombre(dto.getNombre())
                .descripcion(dto.getDescripcion())
                .color(dto.getColor())
                .usuario(usuario)
                .build();

        return mapeadorService.toTipoTareaDTO(tipoTareaRepository.save(tipoTarea));
    }

    @Transactional
    public TipoTareaDTO actualizarTipo(Long id, TipoTareaActualizacionDTO dto, String email) {
        TipoTarea tipoTarea = tipoTareaRepository.findByIdAndUsuarioEmail(id, email)
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de tarea no encontrado"));

        if (dto.getNombre() != null &&
                !dto.getNombre().equalsIgnoreCase(tipoTarea.getNombre()) &&
                tipoTareaRepository.existsByNombreIgnoreCaseAndUsuarioEmail(dto.getNombre(), email)) {
            throw new ResourceConflictException("Ya existe un tipo de tarea con ese nombre");
        }

        if (dto.getNombre() != null) tipoTarea.setNombre(dto.getNombre());
        if (dto.getDescripcion() != null) tipoTarea.setDescripcion(dto.getDescripcion());
        if (dto.getColor() != null) tipoTarea.setColor(dto.getColor());

        return mapeadorService.toTipoTareaDTO(tipoTareaRepository.save(tipoTarea));
    }

    @Transactional
    public void eliminarTipo(Long id, String email) {
        TipoTarea tipoTarea = tipoTareaRepository.findByIdAndUsuarioEmail(id, email)
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de tarea no encontrado"));

        if (tareaRepository.existsByTipoTareaIdAndUsuarioEmail(id, email)) {
            throw new ResourceConflictException(
                    "No puedes eliminar este tipo de tarea porque tiene tareas asociadas"
            );
        }

        tipoTareaRepository.delete(tipoTarea);
    }
}