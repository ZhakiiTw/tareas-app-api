package com.tareas.app.service.util;

import com.tareas.app.dto.TareaCreacionDTO;
import com.tareas.app.dto.TareaDTO;
import com.tareas.app.dto.TipoTareaDTO;
import com.tareas.app.dto.UsuarioDTO;
import com.tareas.app.model.Tarea;
import com.tareas.app.model.TipoTarea;
import com.tareas.app.model.Usuario;
import org.springframework.stereotype.Component;

@Component
public class MapeadorService {

    public UsuarioDTO toUsuarioDTO(Usuario usuario) {
        if (usuario == null) return null;
        return new UsuarioDTO(usuario.getId(), usuario.getUsername(), usuario.getEmail());
    }

    public TipoTareaDTO toTipoTareaDTO(TipoTarea tipoTarea) {
        if (tipoTarea == null) return null;
        return new TipoTareaDTO(
                tipoTarea.getId(),
                tipoTarea.getNombre(),
                tipoTarea.getDescripcion(),
                tipoTarea.getColor()
        );
    }

    public TareaDTO toTareaDTO(Tarea tarea) {
        if (tarea == null) return null;
        return new TareaDTO(
                tarea.getId(),
                tarea.getTitulo(),
                tarea.getDescripcion(),
                tarea.getFecha(),
                tarea.getCompletada(),
                tarea.getUrgencia(),
                tarea.getUsuario() != null ? tarea.getUsuario().getId() : null,
                tarea.getTipoTarea() != null ? tarea.getTipoTarea().getId() : null,
                tarea.getTipoTarea() != null ? tarea.getTipoTarea().getNombre() : null,
                tarea.getTipoTarea() != null ? tarea.getTipoTarea().getColor() : null
        );
    }

    public Tarea toTareaEntity(TareaCreacionDTO dto, Usuario usuario, TipoTarea tipoTarea) {
        if (dto == null) return null;

        Boolean completada = dto.getCompletada() != null ? dto.getCompletada() : false;
        Integer urgencia = dto.getUrgencia() != null ? dto.getUrgencia() : 0;

        return Tarea.builder()
                .titulo(dto.getTitulo())
                .descripcion(dto.getDescripcion())
                .fecha(dto.getFecha())
                .completada(completada)
                .urgencia(urgencia)
                .usuario(usuario)
                .tipoTarea(tipoTarea)
                .build();
    }
}