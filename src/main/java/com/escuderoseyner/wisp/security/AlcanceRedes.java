package com.escuderoseyner.wisp.security;

import com.escuderoseyner.wisp.model.Red;
import com.escuderoseyner.wisp.repository.UsuarioRepository;
import com.escuderoseyner.wisp.service.AccesoDenegadoException;
import com.escuderoseyner.wisp.service.Alcance;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

// Redes del usuario que hace la petición. Se leen de la base de datos en cada petición
// (no van en el token): si el admin le quita una red a un operador, deja de verla al instante.
@Component
public class AlcanceRedes {

    private final UsuarioRepository usuarioRepository;

    public AlcanceRedes(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public Alcance actual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new AccesoDenegadoException();
        }
        Set<String> autoridades = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        if (autoridades.contains("ROLE_ADMIN")) {
            return Alcance.admin();
        }
        if (autoridades.contains("ROLE_OPERADOR")) {
            Set<Integer> redes = usuarioRepository.findConRedesByUsername(auth.getName())
                    .map(u -> u.getRedes().stream().map(Red::getId).collect(Collectors.toSet()))
                    .orElse(Set.of());
            return Alcance.operador(redes);
        }
        // Cualquier otro (ej: un CLIENTE que llegara aquí): sin ninguna red
        return Alcance.operador(Set.of());
    }
}
