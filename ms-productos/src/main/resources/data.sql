-- Semilla inicial del catalogo. Se ejecuta en cada arranque
-- (spring.sql.init.mode=always), por eso la guarda WHERE NOT EXISTS del
-- final: solo inserta si la tabla esta vacia. Sin ella, cada reinicio
-- duplicaria los ocho productos, porque el esquema ya no se recrea.
INSERT INTO productos (nombre, descripcion, precio, stock, activo)
SELECT nombre, descripcion, precio, stock, activo
FROM (VALUES
    ('Taladro percutor 650W', 'Taladro percutor electrico de uso domestico', 34990, 15, true),
    ('Sierra circular 1200W', 'Sierra circular para madera y paneles', 59990, 8, true),
    ('Set de destornilladores', 'Set de 12 destornilladores de precision', 12990, 30, true),
    ('Escalera de aluminio 6 peldanos', 'Escalera plegable resistente hasta 120kg', 45990, 10, true),
    ('Martillo de carpintero', 'Martillo con mango de fibra de vidrio', 8990, 25, true),
    ('Cinta metrica 5m', 'Cinta metrica retractil con freno', 3990, 50, true),
    ('Guantes de trabajo', 'Guantes de cuero reforzado talla unica', 6990, 40, true),
    ('Casco de seguridad', 'Casco de seguridad industrial ajustable', 9990, 0, false)
) AS semilla(nombre, descripcion, precio, stock, activo)
WHERE NOT EXISTS (SELECT 1 FROM productos);
