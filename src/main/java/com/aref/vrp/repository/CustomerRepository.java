package com.aref.vrp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.aref.vrp.entity.Customer;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
}
