package it.unipi.findyourdoc.dto.mongo;

public record DoctorProjection(String npi, Float avgRating, Integer ratingCount) {}