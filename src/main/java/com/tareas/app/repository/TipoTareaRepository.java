package com.tareas.app.repository;

import com.tareas.app.model.TipoTarea;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TipoTareaRepository extends JpaRepository<TipoTarea, Long> {

    List<TipoTarea> findByUsuarioEmailOrderByNombreAsc(String email);

    Optional<TipoTarea> findByIdAndUsuarioEmail(Long id, String email);

    Optional<TipoTarea> findByNombreIgnoreCaseAndUsuarioEmail(String nombre, String email);

    boolean existsByNombreIgnoreCaseAndUsuarioEmail(String nombre, String email);

    boolean existsByIdAndUsuarioEmail(Long id, String email);
}