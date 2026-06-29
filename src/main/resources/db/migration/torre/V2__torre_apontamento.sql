-- =====================================================================
-- Torre - apontamento: perfil CHAPA (mão de obra esporádica sem app).
-- O líder adiciona o chapa à atividade pelo nome; o chapa não loga no app.
-- =====================================================================
INSERT INTO perfil (codigo, descricao)
SELECT 'CHAPA', 'Chapa - mão de obra esporádica, sem acesso ao app'
WHERE NOT EXISTS (SELECT 1 FROM perfil WHERE codigo = 'CHAPA');
