package com.Servicio.RecargasMovil.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import com.Servicio.RecargasMovil.model.OperadorMovil;
import com.Servicio.RecargasMovil.model.PaqueteMovil;
import com.Servicio.RecargasMovil.model.CategoriaPaquete;
import com.Servicio.RecargasMovil.repository.OperadorMovilRepository;
import com.Servicio.RecargasMovil.repository.PaqueteMovilRepository;

@ExtendWith(MockitoExtension.class)
class MobileCatalogInitializerTest {

    @Mock
    private OperadorMovilRepository operadores;

    @Mock
    private PaqueteMovilRepository paquetes;

    @InjectMocks
    private MobileCatalogInitializer initializer;

    @Test
    void createsClaroPrepagoPostpagoAndOtherOperatorCatalogs() throws Exception {
        OperadorMovil claro = new OperadorMovil();
        claro.setId("claro-1");
        claro.setNombre("Claro");
        OperadorMovil tigo = new OperadorMovil();
        tigo.setId("tigo-1");
        tigo.setNombre("Tigo");
        OperadorMovil exito = new OperadorMovil();
        exito.setId("exito-1");
        exito.setNombre("Móvil Éxito");
        OperadorMovil movistar = new OperadorMovil();
        movistar.setId("movistar-1");
        movistar.setNombre("Movistar");
        when(operadores.findByNombreIgnoreCase("Claro")).thenReturn(Optional.of(claro));
        when(operadores.findByNombreIgnoreCase("Tigo")).thenReturn(Optional.of(tigo));
        when(operadores.findByNombreIgnoreCase("Móvil Éxito")).thenReturn(Optional.of(exito));
        when(operadores.findByNombreIgnoreCase("Movistar")).thenReturn(Optional.of(movistar));
        when(paquetes.findByOperadorIdAndNombreIgnoreCase(anyString(), anyString()))
                .thenReturn(Optional.empty());

        initializer.run(new DefaultApplicationArguments());

        ArgumentCaptor<PaqueteMovil> captor = ArgumentCaptor.forClass(PaqueteMovil.class);
        verify(paquetes, times(72)).save(captor.capture());
        List<PaqueteMovil> seededPackages = captor.getAllValues();
        assertThat(seededPackages).extracting(mobilePackage -> mobilePackage.getPrecio().intValue())
                .containsExactlyInAnyOrder(
                        3000, 3000, 5000, 5500, 7000, 9000,
                        6000, 9000, 10000, 15000, 20000, 20000, 25000, 35000, 45000,
                        35000, 24900, 19900, 9900, 7500, 5000, 2500,
                        280000, 199200, 159200, 59700, 74700, 105000, 30000,
                        1000, 2000, 5000, 10000, 20000,
                        2500, 15000, 30000,
                        35000, 20000, 15000, 10000, 9000, 3000, 8500, 30000, 58000, 120000,
                        25000, 41900, 43900, 44900, 46900, 46900, 47900, 49900, 52900,
                        53900, 53900, 55900, 56900, 60900, 62900, 65900, 75900, 75900,
                        83900, 86900, 86950, 93900, 99900, 104900, 128900);
        assertThat(seededPackages.stream().filter(item -> "tigo-1".equals(item.getOperadorId())))
                .hasSize(9)
                .allSatisfy(mobilePackage -> assertThat(mobilePackage.isActivo()).isTrue());
        List<PaqueteMovil> claroPackages = seededPackages.stream()
                .filter(item -> "claro-1".equals(item.getOperadorId()))
                .toList();
        assertThat(claroPackages).hasSize(31);
        assertThat(claroPackages.stream()
                .filter(item -> item.getCategoria() == CategoriaPaquete.PREPAGO)).hasSize(6);
        assertThat(claroPackages.stream()
                .filter(item -> item.getCategoria() == CategoriaPaquete.POSTPAGO)).hasSize(25);
        assertThat(claroPackages)
                .allSatisfy(mobilePackage -> assertThat(mobilePackage.isActivo()).isTrue());
        List<PaqueteMovil> exitoPackages = seededPackages.stream()
                .filter(item -> "exito-1".equals(item.getOperadorId()))
                .toList();
        assertThat(exitoPackages).hasSize(22);
        assertThat(exitoPackages.stream()
                .filter(item -> item.getCategoria() == CategoriaPaquete.PAQUETE)).hasSize(7);
        assertThat(exitoPackages.stream()
                .filter(item -> item.getCategoria() == CategoriaPaquete.PREPAGO)).hasSize(7);
        assertThat(exitoPackages.stream()
                .filter(item -> item.getCategoria() == CategoriaPaquete.RECARGA_DINERO)).hasSize(5);
        assertThat(exitoPackages.stream()
                .filter(item -> item.getCategoria() == CategoriaPaquete.BOLSA_DATOS)).hasSize(3);
        List<PaqueteMovil> movistarPackages = seededPackages.stream()
                .filter(item -> "movistar-1".equals(item.getOperadorId()))
                .toList();
        assertThat(movistarPackages).hasSize(10).allSatisfy(
                mobilePackage -> assertThat(mobilePackage.isActivo()).isTrue());
        assertThat(movistarPackages.stream()
                .filter(item -> item.getCategoria() == CategoriaPaquete.MOVISTAR_PRO)).hasSize(5);
        assertThat(movistarPackages.stream()
                .filter(item -> item.getCategoria() == CategoriaPaquete.OTROS_PAQUETES)).hasSize(2);
        assertThat(movistarPackages.stream()
                .filter(item -> item.getCategoria() == CategoriaPaquete.ROAMING)).hasSize(3);
        verify(operadores, never()).save(any(OperadorMovil.class));
    }

    @Test
    void doesNotDuplicateOperatorsOrPackagesAlreadyInDatabase() throws Exception {
        OperadorMovil claro = new OperadorMovil();
        claro.setId("claro-1");
        OperadorMovil tigo = new OperadorMovil();
        tigo.setId("tigo-1");
        OperadorMovil exito = new OperadorMovil();
        exito.setId("exito-1");
        OperadorMovil movistar = new OperadorMovil();
        movistar.setId("movistar-1");
        when(operadores.findByNombreIgnoreCase("Claro")).thenReturn(Optional.of(claro));
        when(operadores.findByNombreIgnoreCase("Tigo")).thenReturn(Optional.of(tigo));
        when(operadores.findByNombreIgnoreCase("Móvil Éxito")).thenReturn(Optional.of(exito));
        when(operadores.findByNombreIgnoreCase("Movistar")).thenReturn(Optional.of(movistar));
        when(paquetes.findByOperadorIdAndNombreIgnoreCase(anyString(), anyString()))
                .thenAnswer(invocation -> {
                    PaqueteMovil existing = new PaqueteMovil();
                    String operatorId = invocation.getArgument(0);
                    String name = invocation.getArgument(1);
                    existing.setOperadorId(operatorId);
                    existing.setNombre(name);
                    existing.setCategoria(existingCategory(operatorId, name));
                    return Optional.of(existing);
                });

        initializer.run(new DefaultApplicationArguments());

        verify(paquetes, never()).save(any(PaqueteMovil.class));
        verify(operadores, never()).save(any(OperadorMovil.class));
    }

    @Test
    void migratesExistingClaroPrepaidPackagesIntoPrepagoCategory() throws Exception {
        OperadorMovil claro = new OperadorMovil();
        claro.setId("claro-1");
        OperadorMovil tigo = new OperadorMovil();
        tigo.setId("tigo-1");
        OperadorMovil exito = new OperadorMovil();
        exito.setId("exito-1");
        OperadorMovil movistar = new OperadorMovil();
        movistar.setId("movistar-1");
        PaqueteMovil legacyClaroPackage = new PaqueteMovil();
        legacyClaroPackage.setOperadorId("claro-1");
        legacyClaroPackage.setNombre("Todo incluido 1 día · Claro Pay");
        when(operadores.findByNombreIgnoreCase("Claro")).thenReturn(Optional.of(claro));
        when(operadores.findByNombreIgnoreCase("Tigo")).thenReturn(Optional.of(tigo));
        when(operadores.findByNombreIgnoreCase("Móvil Éxito")).thenReturn(Optional.of(exito));
        when(operadores.findByNombreIgnoreCase("Movistar")).thenReturn(Optional.of(movistar));
        when(paquetes.findByOperadorIdAndNombreIgnoreCase(anyString(), anyString()))
                .thenAnswer(invocation -> "claro-1".equals(invocation.getArgument(0))
                        && legacyClaroPackage.getNombre().equals(invocation.getArgument(1))
                                ? Optional.of(legacyClaroPackage)
                                : Optional.empty());
        when(paquetes.save(any(PaqueteMovil.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        initializer.run(new DefaultApplicationArguments());

        assertThat(legacyClaroPackage.getCategoria()).isEqualTo(CategoriaPaquete.PREPAGO);
        verify(paquetes).save(legacyClaroPackage);
    }

    private static CategoriaPaquete existingCategory(String operatorId, String name) {
        if ("claro-1".equals(operatorId)) {
            return name.startsWith("Todo incluido") || name.startsWith("Todo Incluido")
                    ? CategoriaPaquete.PREPAGO
                    : CategoriaPaquete.POSTPAGO;
        }
        if ("exito-1".equals(operatorId)) {
            if (name.startsWith("Recarga de")) {
                return CategoriaPaquete.RECARGA_DINERO;
            }
            if (name.startsWith("1 GB") || name.startsWith("15 GB") || name.startsWith("35 GB")) {
                return CategoriaPaquete.BOLSA_DATOS;
            }
            return name.contains("meses") ? CategoriaPaquete.PREPAGO : CategoriaPaquete.PAQUETE;
        }
        if ("movistar-1".equals(operatorId)) {
            if (name.startsWith("PRO ·")) {
                return CategoriaPaquete.MOVISTAR_PRO;
            }
            if (name.startsWith("Roaming ·")) {
                return CategoriaPaquete.ROAMING;
            }
            return CategoriaPaquete.OTROS_PAQUETES;
        }
        return CategoriaPaquete.PAQUETE;
    }
}
