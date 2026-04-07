package com.tareas.app.service.util;

import com.tareas.app.dto.TareaCreacionDTO;
import com.tareas.app.dto.TareaDTO;
import com.tareas.app.dto.UsuarioDTO;
import com.tareas.app.model.Tarea;
import com.tareas.app.model.Usuario;
import org.springframework.stereotype.Component;

@Component
public class MapeadorService {

    public UsuarioDTO toUsuarioDTO(Usuario usuario) {
        if (usuario == null) return null;
        return new UsuarioDTO(usuario.getId(), usuario.getUsername(), usuario.getEmail());
    }

    public TareaDTO toTareaDTO(Tarea tarea) {
        if (tarea == null) return null;
        return new TareaDTO(
                tarea.getId(),
                tarea.getTitulo(),
                tarea.getDescripcion(),
                tarea.getFecha(),
                tarea.getCompletada(),
                tarea.getUsuario() != null ? tarea.getUsuario().getId() : null
        );
    }

    public Tarea toTareaEntity(TareaCreacionDTO dto, Usuario usuario) {
        if (dto == null) return null;

        Boolean completada = dto.getCompletada() != null ? dto.getCompletada() : false;

        return Tarea.builder()
                .titulo(dto.getTitulo())
                .descripcion(dto.getDescripcion())
                .fecha(dto.getFecha())
                .completada(completada)
                .usuario(usuario)
                .build();
    }
}