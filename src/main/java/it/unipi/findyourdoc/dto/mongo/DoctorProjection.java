package it.unipi.findyourdoc.dto.mongo;

public record DoctorProjection(String npi, Double avgRating, Integer ratingCount) {}