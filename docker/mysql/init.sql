-- Initialize all databases for the FYP Banking System
CREATE DATABASE IF NOT EXISTS auth_db;
CREATE DATABASE IF NOT EXISTS cbc_db;
CREATE DATABASE IF NOT EXISTS txn_db;
CREATE DATABASE IF NOT EXISTS audit_db;
CREATE DATABASE IF NOT EXISTS kyc_db;
CREATE DATABASE IF NOT EXISTS fraud_db;

-- Grant full access to the app user on all databases
GRANT ALL PRIVILEGES ON auth_db.* TO 'fyp_user'@'%';
GRANT ALL PRIVILEGES ON cbc_db.* TO 'fyp_user'@'%';
GRANT ALL PRIVILEGES ON txn_db.* TO 'fyp_user'@'%';
GRANT ALL PRIVILEGES ON audit_db.* TO 'fyp_user'@'%';
GRANT ALL PRIVILEGES ON kyc_db.* TO 'fyp_user'@'%';
GRANT ALL PRIVILEGES ON fraud_db.* TO 'fyp_user'@'%';
FLUSH PRIVILEGES;
