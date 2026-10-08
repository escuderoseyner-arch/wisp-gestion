package com.escuderoseyner.wisp.dto;

import com.escuderoseyner.wisp.model.ModoRed;

// Los datos de una red que comparten "crear" y "editar". El token va aparte: solo al crear o al cambiarlo.
public interface DatosRed {

    String nombre();

    String colaPadre();

    String colasProtegidas();

    Integer intervaloSegundos();

    ModoRed modo();
}
