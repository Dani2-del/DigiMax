package com.transcaribe.transcaribe.Repository;

import com.transcaribe.transcaribe.Model.Bus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.transcaribe.transcaribe.Model.SistemaTransporte;
import org.springframework.data.mongodb.repository.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public interface BusRepository extends MongoRepository<Bus, String> {
    Optional<Bus> findByPlaca(String placa);

    @Query("{ 'sistema_transporte': ?0 }")
    List<Bus> buscarPorSistemaExacto(SistemaTransporte sistema);

    @Query("{ 'sistema_transporte': null }")
    List<Bus> buscarSinSistema();

    /**
     * Buses de un servicio, consultados en la base de datos en lugar de cargar todos
     * los buses. Los buses antiguos sin servicio cuentan como Transcaribe, igual que
     * Bus#getSistemaTransporte().
     */
    default List<Bus> findBySistema(SistemaTransporte sistema) {
        if (sistema == null) {
            return List.of();
        }
        List<Bus> buses = new ArrayList<>(buscarPorSistemaExacto(sistema));
        if (sistema == SistemaTransporte.TRANSCARIBE) {
            buses.addAll(buscarSinSistema());
        }
        return buses;
    }
}

//Repositorios , No hay nada del otro mundo aqui

//⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿
//⣿⣿⣿⣿⣿⣿⣿⣿⡿⠿⠛⠛⠛⢻⡻⠿⣿⣿⣿⣿⣿⣿⣿⣿
//⣿⣿⣿⣿⣿⣿⣟⣫⡾⠛⠛⠛⠛⠛⠛⠿⣾⣽⡻⣿⣿⣿⣿⣿
//⣿⣿⣿⣿⣿⡟⣼⠏⠄⠄⠄⠄⠄⠄⣀⣀⡀⣙⣿⣎⢿⣿⣿⣿
//⣿⣿⣿⣿⣿⢹⡟⠄⠄⠄⣰⡾⠟⠛⠛⠛⠛⠛⠛⠿⣮⡻⣿⣿
//⣿⡿⢟⣻⣟⣽⠇⠄⠄⠄⣿⠄⠄⠄⠄⠄⠄⠄⠄⠄⠈⢿⡹⣿
//⡟⣼⡟⠉⠉⣿⠄⠄⠄⠄⢿⡄⠄⠄⠄⠄⠄⠄⠄⠄⠄⣼⢟⣿
//⣇⣿⠁⠄⠄⣿⠄⠄⠄⠄⠘⢿⣦⣄⣀⣀⣀⣀⣤⡴⣾⣏⣾⣿
//⡇⣿⠄⠄⠄⣿⠄⠄⠄⠄⠄⠄⠈⠉⠛⠋⠉⠉⠄⠄⢻⣿⣿⣿
//⢃⣿⠄⠄⠄⣿⠄⠄⠄⠄⠄⠄⠄⠄⠄⠄⠄⠄⠄⠄⢸⣧⣿⣿
//⡻⣿⠄⠄⠄⣿⠄⠄⠄⠄⠄⠄⠄⠄⠄⠄⠄⠄⠄⠄⣸⣧⣿⣿
//⡇⣿⠄⠄⠄⣿⠄⠄⠄⠄⠄⠄⠄⠄⠄⠄⠄⠄⠄⠄⣿⢹⣿⣿
//⣿⡸⢷⣤⣤⣿⡀⠄⠄⠄⠄⢠⣤⣄⣀⣀⣀⠄⠄⢠⣿⣿⣿⣿
//⣿⣿⣷⣿⣷⣿⡇⠄⠄⠄⠄⢸⡏⡍⣿⡏⠄⠄⠄⢸⡏⣿⣿⣿
//⣿⣿⣿⣿⣿⢼⡇⠄⠄⠄⠄⣸⡇⣷⣻⣆⣀⣀⣀⣼⣻⣿⣿⣿
//⣿⣿⣿⣿⣿⣜⠿⢦⣤⣤⡾⢟⣰⣿⣷⣭⣯⣭⣯⣥⣿⣿⣿⣿
//⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿