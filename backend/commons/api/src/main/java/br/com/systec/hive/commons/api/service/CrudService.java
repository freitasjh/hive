package br.com.systec.hive.commons.api.service;

public interface CrudService<T> {

    T create(T obj);

    T update(T obj);

    T delete(T obj);
}
