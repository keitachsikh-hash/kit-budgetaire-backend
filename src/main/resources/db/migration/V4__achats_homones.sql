-- Nouvelles catégories de dépense « Achats » et « Homones » pour les utilisateurs existants.
INSERT INTO category (user_id, name, type, icon, color)
SELECT u.id, 'Achats', 'EXPENSE', 'cart', '#fd7e14' FROM app_user u
WHERE NOT EXISTS (SELECT 1 FROM category c WHERE c.user_id = u.id AND c.name = 'Achats' AND c.type = 'EXPENSE');

INSERT INTO category (user_id, name, type, icon, color)
SELECT u.id, 'Homones', 'EXPENSE', 'droplet', '#d63384' FROM app_user u
WHERE NOT EXISTS (SELECT 1 FROM category c WHERE c.user_id = u.id AND c.name = 'Homones' AND c.type = 'EXPENSE');
