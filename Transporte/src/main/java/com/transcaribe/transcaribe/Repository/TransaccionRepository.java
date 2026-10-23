package com.transcaribe.transcaribe.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import com.transcaribe.transcaribe.Model.Transaccion;
import com.transcaribe.transcaribe.Model.SistemaTransporte;
import com.transcaribe.transcaribe.Model.Usuario;

@Repository
public interface TransaccionRepository extends MongoRepository<Transaccion, String> {
    @Query("{ 'usuario_id': ?0 }")
    List<Transaccion> findByUsuarioId(String usuarioId);

    @Query("{}")
    Stream<Transaccion> streamAll();

    @Query("{ 'fecha': { '$gte': ?0, '$lt': ?1 } }")
    Stream<Transaccion> streamByFechaEnRango(LocalDateTime inicio, LocalDateTime fin);

    @Query("{ '$or': [ { 'sistema_transporte': 'TRANSCARIBE' }, { 'sistema_transporte': null } ] }")
    Stream<Transaccion> streamTranscaribe();

    @Query("{ 'sistema_transporte': ?0 }")
    Stream<Transaccion> streamBySistemaTransporte(SistemaTransporte sistemaTransporte);

    @Query("{ 'fecha': { '$gte': ?0, '$lt': ?1 }, '$or': [ { 'sistema_transporte': 'TRANSCARIBE' }, { 'sistema_transporte': null } ] }")
    Stream<Transaccion> streamTranscaribeByFechaEnRango(LocalDateTime inicio, LocalDateTime fin);

    @Query("{ 'fecha': { '$gte': ?0, '$lt': ?1 }, 'sistema_transporte': ?2 }")
    Stream<Transaccion> streamBySistemaTransporteAndFechaEnRango(
            LocalDateTime inicio, LocalDateTime fin, SistemaTransporte sistemaTransporte);
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
