package com.adventurebook.save;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GameSaveRepository extends JpaRepository<GameSave, String> {

    @Override
    List<GameSave> findAll();
}
