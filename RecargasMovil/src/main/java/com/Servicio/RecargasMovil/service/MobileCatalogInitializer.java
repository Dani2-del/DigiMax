package com.Servicio.RecargasMovil.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.Servicio.RecargasMovil.model.CategoriaPaquete;
import com.Servicio.RecargasMovil.model.OperadorMovil;
import com.Servicio.RecargasMovil.model.PaqueteMovil;
import com.Servicio.RecargasMovil.repository.OperadorMovilRepository;
import com.Servicio.RecargasMovil.repository.PaqueteMovilRepository;

@Component
public class MobileCatalogInitializer implements ApplicationRunner {

    private static final List<OperatorCatalog> INITIAL_CATALOGS = List.of(
            new OperatorCatalog("Claro", List.of(
                    new PackageDefinition(CategoriaPaquete.PREPAGO,
                            "Todo incluido 1 día · Claro Pay",
                            "170 MB, WhatsApp y 50 minutos todo destino. Paquete exclusivo Claro Pay.",
                            "3000"),
                    new PackageDefinition(CategoriaPaquete.PREPAGO,
                            "Todo incluido 1 día · WhatsApp",
                            "150 MB, WhatsApp y 50 minutos todo destino.",
                            "3000"),
                    new PackageDefinition(CategoriaPaquete.PREPAGO,
                            "Todo incluido 7 días · Bienvenida",
                            "2 GB, WhatsApp, Facebook, X (Twitter) y minutos todo destino ilimitados.",
                            "5000"),
                    new PackageDefinition(CategoriaPaquete.PREPAGO,
                            "Todo incluido 3 días",
                            "400 MB, WhatsApp, Facebook, X (Twitter) y minutos todo destino ilimitados.",
                            "5500"),
                    new PackageDefinition(CategoriaPaquete.PREPAGO,
                            "Todo Incluido + WTF + Salud en Línea · 3 días",
                            "400 MB, WhatsApp, Facebook, X (Twitter), WTF, Salud en Línea y minutos ilimitados.",
                            "7000"),
                    new PackageDefinition(CategoriaPaquete.PREPAGO,
                            "Todo incluido 6 días",
                            "1,4 GB, WhatsApp, Facebook, X (Twitter) y minutos todo destino ilimitados.",
                            "9000"),
                    new PackageDefinition(CategoriaPaquete.POSTPAGO,
                            "50183 - Todo incluido 20 Días - WIN Y PRIME E.MOVIL",
                            "Plan pospago con cargo fijo mensual de $25.000 COP.",
                            "25000"),
                    new PackageDefinition(CategoriaPaquete.POSTPAGO,
                            "Conectados 25.2 V 4.2",
                            "Plan pospago con cargo fijo mensual.",
                            "41900"),
                    new PackageDefinition(CategoriaPaquete.POSTPAGO,
                            "Conectados 25 V 43.9_AP1",
                            "Plan pospago con cargo fijo mensual.",
                            "43900"),
                    new PackageDefinition(CategoriaPaquete.POSTPAGO,
                            "Conectados 26.1 V 44.9",
                            "Plan pospago con cargo fijo mensual.",
                            "44900"),
                    new PackageDefinition(CategoriaPaquete.POSTPAGO,
                            "Conectados 26.1 E 46.9",
                            "Plan pospago con cargo fijo mensual.",
                            "46900"),
                    new PackageDefinition(CategoriaPaquete.POSTPAGO,
                            "Conectados 25 V 46.9_AP1",
                            "Plan pospago con cargo fijo mensual.",
                            "46900"),
                    new PackageDefinition(CategoriaPaquete.POSTPAGO,
                            "Conectados 26.1 V 47.9",
                            "Plan pospago con cargo fijo mensual.",
                            "47900"),
                    new PackageDefinition(CategoriaPaquete.POSTPAGO,
                            "Conectados 26.3 V 49.9_AP1",
                            "Plan pospago con cargo fijo mensual.",
                            "49900"),
                    new PackageDefinition(CategoriaPaquete.POSTPAGO,
                            "Conectados 26.3 V 52.9_AP1",
                            "Plan pospago con cargo fijo mensual.",
                            "52900"),
                    new PackageDefinition(CategoriaPaquete.POSTPAGO,
                            "Conectados 26.1 E 53.9_CN_EC",
                            "Plan pospago con cargo fijo mensual.",
                            "53900"),
                    new PackageDefinition(CategoriaPaquete.POSTPAGO,
                            "Conectados 26.1 V 53.9",
                            "Plan pospago con cargo fijo mensual.",
                            "53900"),
                    new PackageDefinition(CategoriaPaquete.POSTPAGO,
                            "Conectados 26.1 V 55.9",
                            "Plan pospago con cargo fijo mensual.",
                            "55900"),
                    new PackageDefinition(CategoriaPaquete.POSTPAGO,
                            "Conectados 26.3 V 56.9_AP1",
                            "Plan pospago con cargo fijo mensual.",
                            "56900"),
                    new PackageDefinition(CategoriaPaquete.POSTPAGO,
                            "Conectados Win 25.2 V 6.1",
                            "Plan pospago con cargo fijo mensual.",
                            "60900"),
                    new PackageDefinition(CategoriaPaquete.POSTPAGO,
                            "Conectados Netflix 25 V 6.1",
                            "Plan pospago con cargo fijo mensual.",
                            "62900"),
                    new PackageDefinition(CategoriaPaquete.POSTPAGO,
                            "Conectados 26.3 V 65.9",
                            "Plan pospago con cargo fijo mensual.",
                            "65900"),
                    new PackageDefinition(CategoriaPaquete.POSTPAGO,
                            "Conectados 26.3 V 75.9",
                            "Plan pospago con cargo fijo mensual.",
                            "75900"),
                    new PackageDefinition(CategoriaPaquete.POSTPAGO,
                            "Conectados Netflix 25 V 7.2",
                            "Plan pospago con cargo fijo mensual.",
                            "75900"),
                    new PackageDefinition(CategoriaPaquete.POSTPAGO,
                            "Conectados Netflix 25 V 8.1",
                            "Plan pospago con cargo fijo mensual.",
                            "83900"),
                    new PackageDefinition(CategoriaPaquete.POSTPAGO,
                            "Conectados Netflix 25 V 8.2",
                            "Plan pospago con cargo fijo mensual.",
                            "86900"),
                    new PackageDefinition(CategoriaPaquete.POSTPAGO,
                            "Claro Voz ilimitado V8.1",
                            "Plan pospago con cargo fijo mensual.",
                            "86950"),
                    new PackageDefinition(CategoriaPaquete.POSTPAGO,
                            "Conectados Disney 25 V 9.1",
                            "Plan pospago con cargo fijo mensual.",
                            "93900"),
                    new PackageDefinition(CategoriaPaquete.POSTPAGO,
                            "Conectados 26.3 V 99.9",
                            "Plan pospago con cargo fijo mensual.",
                            "99900"),
                    new PackageDefinition(CategoriaPaquete.POSTPAGO,
                            "Conectados Netflix 25 V 10.1",
                            "Plan pospago con cargo fijo mensual.",
                            "104900"),
                    new PackageDefinition(CategoriaPaquete.POSTPAGO,
                            "Conectados Netflix 25 V 12.1",
                            "Plan pospago con cargo fijo mensual.",
                            "128900"))),
            new OperatorCatalog("Tigo", List.of(
                    new PackageDefinition(
                            "400 MB · 3 días",
                            "400 MB, minutos y SMS ilimitados; incluye Facebook y WhatsApp.",
                            "6000"),
                    new PackageDefinition(
                            "2,8 GB · 6 días",
                            "Doble de datos: 1,4 GB + 1,4 GB adicionales, minutos y SMS ilimitados; incluye Facebook y WhatsApp.",
                            "9000"),
                    new PackageDefinition(
                            "4 GB · 7 días",
                            "Doble de datos: 2 GB + 2 GB adicionales, minutos y SMS ilimitados; incluye Facebook y WhatsApp.",
                            "10000"),
                    new PackageDefinition(
                            "7 GB · 10 días",
                            "Doble de datos: 3,5 GB + 3,5 GB adicionales, minutos y SMS ilimitados; incluye Facebook y WhatsApp.",
                            "15000"),
                    new PackageDefinition(
                            "20 GB · 20 días",
                            "Doble de datos: 10 GB + 10 GB adicionales, minutos y SMS ilimitados; incluye Facebook y WhatsApp.",
                            "20000"),
                    new PackageDefinition(
                            "24 GB · 15 días",
                            "Doble de datos: 12 GB + 12 GB adicionales, minutos y SMS ilimitados; incluye Facebook y WhatsApp.",
                            "20000"),
                    new PackageDefinition(
                            "26 GB · 20 días",
                            "Doble de datos: 13 GB + 13 GB adicionales, minutos y SMS ilimitados; incluye Facebook y WhatsApp.",
                            "25000"),
                    new PackageDefinition(
                            "28 GB · 30 días",
                            "Doble de datos: 14 GB + 14 GB adicionales, minutos y SMS ilimitados; incluye Facebook y WhatsApp.",
                            "35000"),
                    new PackageDefinition(
                            "36 GB · 30 días",
                            "Doble de datos: 18 GB + 18 GB adicionales, minutos y SMS ilimitados; incluye Facebook y WhatsApp.",
                            "45000"))),
            new OperatorCatalog("Móvil Éxito", List.of(
                    new PackageDefinition("40 GB · 30 días",
                            "40 GB, aplicaciones incluidas y minutos ilimitados.", "35000"),
                    new PackageDefinition("20 GB · 30 días",
                            "20 GB, aplicaciones incluidas y minutos ilimitados.", "24900"),
                    new PackageDefinition("10 GB · 30 días",
                            "10 GB, aplicaciones incluidas y minutos ilimitados.", "19900"),
                    new PackageDefinition("4 GB · 15 días",
                            "4 GB, aplicaciones incluidas y minutos ilimitados.", "9900"),
                    new PackageDefinition("3 GB · 10 días",
                            "3 GB, aplicaciones incluidas y minutos ilimitados.", "7500"),
                    new PackageDefinition("1,5 GB · 7 días",
                            "1,5 GB, aplicaciones incluidas y minutos ilimitados.", "5000"),
                    new PackageDefinition("400 MB · 3 días",
                            "400 MB, aplicaciones incluidas y minutos ilimitados.", "2500"),
                    new PackageDefinition(CategoriaPaquete.PREPAGO, "480 GB · 12 meses",
                            "40 GB por mes durante 12 meses; aplicaciones y minutos ilimitados. Valor total del paquete.", "280000"),
                    new PackageDefinition(CategoriaPaquete.PREPAGO, "240 GB · 12 meses",
                            "20 GB por mes durante 12 meses; aplicaciones y minutos ilimitados. Valor total del paquete.", "199200"),
                    new PackageDefinition(CategoriaPaquete.PREPAGO, "120 GB · 12 meses",
                            "10 GB por mes durante 12 meses; aplicaciones y minutos ilimitados. Valor total del paquete.", "159200"),
                    new PackageDefinition(CategoriaPaquete.PREPAGO, "40 GB · 4 meses",
                            "10 GB por mes durante 4 meses; aplicaciones y minutos ilimitados. Valor total del paquete.", "59700"),
                    new PackageDefinition(CategoriaPaquete.PREPAGO, "80 GB · 4 meses",
                            "20 GB por mes durante 4 meses; aplicaciones y minutos ilimitados. Valor total del paquete.", "74700"),
                    new PackageDefinition(CategoriaPaquete.PREPAGO, "160 GB · 4 meses",
                            "40 GB por mes durante 4 meses; aplicaciones y minutos ilimitados. Valor total del paquete.", "105000"),
                    new PackageDefinition(CategoriaPaquete.PREPAGO, "30 GB · 3 meses",
                            "10 GB por mes durante 3 meses; aplicaciones y minutos ilimitados. Valor total del paquete.", "30000"),
                    new PackageDefinition(CategoriaPaquete.RECARGA_DINERO, "Recarga de $1.000",
                            "Recarga de saldo móvil por $1.000 COP.", "1000"),
                    new PackageDefinition(CategoriaPaquete.RECARGA_DINERO, "Recarga de $2.000",
                            "Recarga de saldo móvil por $2.000 COP.", "2000"),
                    new PackageDefinition(CategoriaPaquete.RECARGA_DINERO, "Recarga de $5.000",
                            "Recarga de saldo móvil por $5.000 COP.", "5000"),
                    new PackageDefinition(CategoriaPaquete.RECARGA_DINERO, "Recarga de $10.000",
                            "Recarga de saldo móvil por $10.000 COP.", "10000"),
                    new PackageDefinition(CategoriaPaquete.RECARGA_DINERO, "Recarga de $20.000",
                            "Recarga de saldo móvil por $20.000 COP.", "20000"),
                    new PackageDefinition(CategoriaPaquete.BOLSA_DATOS, "1 GB · 1 día",
                            "Bolsita de 1 GB con vigencia de hasta 1 día.", "2500"),
                    new PackageDefinition(CategoriaPaquete.BOLSA_DATOS, "15 GB · 15 días",
                            "Bolsita de 15 GB con vigencia de hasta 15 días.", "15000"),
                    new PackageDefinition(CategoriaPaquete.BOLSA_DATOS, "35 GB · 30 días",
                            "Bolsita de 35 GB con vigencia de hasta 30 días.", "30000"))),
            new OperatorCatalog("Movistar", List.of(
                    new PackageDefinition(CategoriaPaquete.MOVISTAR_PRO, "PRO · 39 GB · 30 días",
                            "Paquete mes con 39 GB (antes 13 GB), 3x1 en datos y apps ilimitadas.",
                            "35000"),
                    new PackageDefinition(CategoriaPaquete.MOVISTAR_PRO, "PRO · 30 GB · 20 días",
                            "30 GB (antes 10 GB), 3x1 en datos y apps ilimitadas.", "20000"),
                    new PackageDefinition(CategoriaPaquete.MOVISTAR_PRO, "PRO · 10,5 GB · 10 días",
                            "10,5 GB (antes 3,5 GB), 3x1 en datos y apps ilimitadas.", "15000"),
                    new PackageDefinition(CategoriaPaquete.MOVISTAR_PRO, "PRO · 6 GB · 7 días",
                            "6 GB (antes 2 GB), 3x1 en datos y apps ilimitadas.", "10000"),
                    new PackageDefinition(CategoriaPaquete.MOVISTAR_PRO, "PRO · 4,2 GB · 6 días",
                            "4,2 GB (antes 1,4 GB), 3x1 en datos y apps ilimitadas.", "9000"),
                    new PackageDefinition(CategoriaPaquete.OTROS_PAQUETES, "Paquete Full · 1 día",
                            "Oferta exclusiva online.", "3000"),
                    new PackageDefinition(CategoriaPaquete.OTROS_PAQUETES,
                            "Voz a todo operador · 1.200 minutos · 30 días",
                            "1.200 minutos con promoción 2x1 para llamadas a todo operador.", "8500"),
                    new PackageDefinition(CategoriaPaquete.ROAMING, "Roaming · 6,5 GB · 7 días",
                            "6,5 GB para navegar fuera del país.", "30000"),
                    new PackageDefinition(CategoriaPaquete.ROAMING, "Roaming · 13 GB · 15 días",
                            "13 GB para navegar fuera del país.", "58000"),
                    new PackageDefinition(CategoriaPaquete.ROAMING, "Roaming · 28 GB · 30 días",
                            "28 GB para navegar fuera del país.", "120000"))));

    private final OperadorMovilRepository operadores;
    private final PaqueteMovilRepository paquetes;

    public MobileCatalogInitializer(
            OperadorMovilRepository operadores, PaqueteMovilRepository paquetes) {
        this.operadores = operadores;
        this.paquetes = paquetes;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (OperatorCatalog catalog : INITIAL_CATALOGS) {
            seedCatalog(catalog);
        }
    }

    private void seedCatalog(OperatorCatalog catalog) {
        OperadorMovil operator = operadores.findByNombreIgnoreCase(catalog.operatorName())
                .orElseGet(() -> createOperator(catalog.operatorName()));

        for (PackageDefinition definition : catalog.packages()) {
            PaqueteMovil mobilePackage = paquetes
                    .findByOperadorIdAndNombreIgnoreCase(operator.getId(), definition.name())
                    .orElse(null);
            if (mobilePackage == null) {
                mobilePackage = new PaqueteMovil();
                mobilePackage.setOperadorId(operator.getId());
                mobilePackage.setNombre(definition.name());
                mobilePackage.setDescripcion(definition.description());
                mobilePackage.setPrecio(new BigDecimal(definition.price()));
                mobilePackage.setCategoria(definition.category());
                mobilePackage.setActivo(true);
                paquetes.save(mobilePackage);
            } else if (mobilePackage.getCategoria() != definition.category()) {
                mobilePackage.setCategoria(definition.category());
                paquetes.save(mobilePackage);
            }
        }
    }

    private OperadorMovil createOperator(String name) {
        OperadorMovil operator = new OperadorMovil();
        operator.setNombre(name);
        operator.setActivo(true);
        return operadores.save(operator);
    }

    private record OperatorCatalog(String operatorName, List<PackageDefinition> packages) {
    }

    private record PackageDefinition(
            CategoriaPaquete category, String name, String description, String price) {
        private PackageDefinition(String name, String description, String price) {
            this(CategoriaPaquete.PAQUETE, name, description, price);
        }
    }
}
