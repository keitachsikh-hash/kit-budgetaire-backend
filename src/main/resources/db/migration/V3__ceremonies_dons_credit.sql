-- 1) « Cérémonies & Dons » devient deux catégories distinctes : « Cérémonies » et « Dons ».
--    (table dérivée : MySQL interdit de lire la table modifiée dans une sous-requête d'UPDATE)
UPDATE category SET name = 'Cérémonies', icon = 'balloon-heart'
WHERE name = 'Cérémonies & Dons' AND type = 'EXPENSE'
  AND user_id NOT IN (SELECT u FROM (SELECT user_id AS u FROM category
                                     WHERE name = 'Cérémonies' AND type = 'EXPENSE') existing);

INSERT INTO category (user_id, name, type, icon, color)
SELECT u.id, 'Cérémonies', 'EXPENSE', 'balloon-heart', '#d63384' FROM app_user u
WHERE NOT EXISTS (SELECT 1 FROM category c WHERE c.user_id = u.id AND c.name = 'Cérémonies' AND c.type = 'EXPENSE');

INSERT INTO category (user_id, name, type, icon, color)
SELECT u.id, 'Dons', 'EXPENSE', 'gift', '#e35d6a' FROM app_user u
WHERE NOT EXISTS (SELECT 1 FROM category c WHERE c.user_id = u.id AND c.name = 'Dons' AND c.type = 'EXPENSE');

-- 2) Nouvelle catégorie de dépense « Crédit » (recharges téléphoniques).
INSERT INTO category (user_id, name, type, icon, color)
SELECT u.id, 'Crédit', 'EXPENSE', 'sim', '#ff7900' FROM app_user u
WHERE NOT EXISTS (SELECT 1 FROM category c WHERE c.user_id = u.id AND c.name = 'Crédit' AND c.type = 'EXPENSE');

-- 3) Opérateur téléphonique d'une recharge de crédit (ORANGE, YAS, EXPRESSO).
ALTER TABLE budget_transaction ADD COLUMN operator VARCHAR(20);
