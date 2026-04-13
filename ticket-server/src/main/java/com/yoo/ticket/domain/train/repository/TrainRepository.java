package com.yoo.ticket.domain.train.repository;

import com.yoo.ticket.domain.train.entity.Train;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainRepository extends JpaRepository<Train, Long> {
}
