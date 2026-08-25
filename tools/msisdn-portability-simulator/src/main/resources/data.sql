INSERT INTO mnp_operator (code, display_name, routing_prefix) VALUES ('TT', 'Tunisie Telecom', 'D001');
INSERT INTO mnp_operator (code, display_name, routing_prefix) VALUES ('OOR', 'Ooredoo', 'D002');
INSERT INTO mnp_operator (code, display_name, routing_prefix) VALUES ('ORA', 'Orange', 'D003');

-- Not ported
INSERT INTO mnp_ported_number (msisdn, donor_operator_code, current_operator_code) VALUES ('+21698000000', 'TT', 'TT');
INSERT INTO mnp_ported_number (msisdn, donor_operator_code, current_operator_code) VALUES ('+21622000000', 'OOR', 'OOR');
INSERT INTO mnp_ported_number (msisdn, donor_operator_code, current_operator_code) VALUES ('+21650000000', 'ORA', 'ORA');

-- Ported
INSERT INTO mnp_ported_number (msisdn, donor_operator_code, current_operator_code, last_ported_at) VALUES ('+21622999999', 'OOR', 'TT', CURRENT_TIMESTAMP);
