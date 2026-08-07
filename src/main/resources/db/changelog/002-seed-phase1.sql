--liquibase formatted sql
--changeset metacloth:003

INSERT INTO avatars(title,image_url,category)
SELECT 'Preset Avatar '||g, 'https://placehold.co/512x512/png?text=Avatar+'||g,
CASE WHEN g<=10 THEN 'CYBERPUNK' WHEN g<=20 THEN 'ANIME' WHEN g<=30 THEN 'GEOMETRIC_MASCOT' ELSE 'RETRO_POP' END
FROM generate_series(1,40) g;

--changeset metacloth:004
INSERT INTO products(name,description,garment_type,base_price,print_surcharge,is_customizable) VALUES
('MC Classic Round Neck','Premium customizable round neck.','ROUND_NECK',799,100,true),
('MC Oversized','Heavyweight premium oversized fit.','OVERSIZED',999,120,true),
('MC Hoodie','Premium heavyweight hoodie.','HOODIE',1499,150,true),
('MC Acid Wash','Streetwear acid wash garment.','ACID_WASH',1099,120,true),
('MC Sports Jersey','Lightweight sports jersey.','SPORTS',899,100,true);

--changeset metacloth:005
INSERT INTO product_variants(product_id,color_name,color_hex,size,stock_quantity,mockup_front_url,mockup_back_url)
SELECT p.id,c.n,c.h,s.sz,25,
'https://placehold.co/800x1000/png?text='||replace(p.name,' ','+')||'+'||c.n||'+'||s.sz,
'https://placehold.co/800x1000/png?text=Back+'||replace(p.name,' ','+')
FROM products p
CROSS JOIN (VALUES ('Obsidian','#0B0B0C'),('Slate','#18181B'),('Violet','#7C3AED'),('White','#F4F4F5')) c(n,h)
CROSS JOIN (VALUES ('S'),('M'),('L'),('XL'),('XXL')) s(sz);
