-- Nouvelle catégorie de dépense « Dépenses inutiles » pour les utilisateurs existants.
INSERT INTO category (user_id, name, type, icon, color)
SELECT u.id, 'Dépenses inutiles', 'EXPENSE', 'exclamation-triangle', '#dc3545' FROM app_user u
WHERE NOT EXISTS (SELECT 1 FROM category c WHERE c.user_id = u.id AND c.name = 'Dépenses inutiles' AND c.type = 'EXPENSE');
