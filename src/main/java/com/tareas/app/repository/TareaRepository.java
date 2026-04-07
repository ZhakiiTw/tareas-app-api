package com.tareas.app.repository;

import com.tareas.app.model.Tarea;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TareaRepository extends JpaRepository<Tarea, Long> {

    // Listar solo las tareas del usuario por email (que es tu "username" en JWT)
    List<Tarea> findByUsuarioEmailOrderByFechaAsc(String email);

    // Para asegurar que el ID pertenece al usuario
    Optional<Tarea> findByIdAndUsuarioEmail(Long id, String email);

    boolean existsByIdAndUsuarioEmail(Long id, String email);

    void deleteByIdAndUsuarioEmail(Long id, String email);
}
