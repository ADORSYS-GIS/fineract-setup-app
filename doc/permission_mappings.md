# Fineract Permission Mappings for Core Banking Roles

This document provides a detailed, technical mapping of Fineract permission codes to the core banking roles defined in the `roles-and-permissions.md` document. The permissions listed here are derived from the `0003-mifosx-permissions-and-authorisation-utf8.sql` file and are intended to grant each role the precise level of access required to perform their duties.

## 1. Account Manager

The Account Manager handles client creation, account opening, and loan initiation.

### Portfolio Permissions
- `CREATE_CLIENT`
- `ACTIVATE_CLIENT`
- `READ_CLIENT`
- `UPDATE_CLIENT`
- `CREATE_SAVINGSACCOUNT`
- `READ_SAVINGSACCOUNT`
- `CREATE_LOAN`
- `READ_LOAN`
- `READ_GROUP`
- `READ_CLIENTIMAGE`
- `CREATE_CLIENTIMAGE`
- `DELETE_CLIENTIMAGE`
- `READ_CLIENTNOTE`
- `CREATE_CLIENTNOTE`
- `UPDATE_CLIENTNOTE`
- `DELETE_CLIENTNOTE`
- `READ_CLIENTIDENTIFIER`
- `CREATE_CLIENTIDENTIFIER`
- `UPDATE_CLIENTIDENTIFIER`
- `DELETE_CLIENTIDENTIFIER`
- `READ_DOCUMENT`
- `CREATE_DOCUMENT`
- `UPDATE_DOCUMENT`
- `DELETE_DOCUMENT`

### Organisation Permissions
- `READ_SAVINGSPRODUCT`
- `READ_LOANPRODUCT`
- `READ_OFFICE`
- `READ_STAFF`

---

## 2. Cashier

The Cashier handles client-facing financial transactions.

### Portfolio Permissions
- `READ_CLIENT`
- `READ_SAVINGSACCOUNT`

### Transaction (Savings) Permissions
- `DEPOSIT_SAVINGSACCOUNT`
- `WITHDRAWAL_SAVINGSACCOUNT`

---

## 3. Branch Manager

The Branch Manager oversees approvals, staff management, and branch-level financial operations.

### Portfolio Permissions
- `READ_CLIENT`
- `READ_SAVINGSACCOUNT`
- `READ_LOAN`
- `READ_GROUP`
- `READ_DOCUMENT`
- `READ_CLIENTIMAGE`
- `READ_CLIENTNOTE`
- `READ_CLIENTIDENTIFIER`

### Organisation Permissions
- `READ_OFFICE`
- `UPDATE_STAFF`
- `READ_STAFF`
- `CREATE_OFFICETRANSACTION`
- `READ_OFFICETRANSACTION`
- `READ_SAVINGSPRODUCT`
- `READ_LOANPRODUCT`
- `READ_CHARGE`
- `READ_FUND`
- `READ_MAKERCHECKER`
- `ALLOCATECASHIER_TELLER`
- `ALLOCATECASHTOCASHIER_TELLER`
- `SETTLECASHFROMCASHIER_TELLER`

### Transaction (Loan) Permissions
- `APPROVE_LOAN`
- `REJECT_LOAN`
- `DISBURSE_LOAN`
- `WITHDRAW_LOAN`

### Transaction (Savings) Permissions
- `APPROVE_SAVINGSACCOUNT`
- `ACTIVATE_SAVINGSACCOUNT` 


### Accounting Permissions
- `CREATE_JOURNALENTRY`
- `REVERSE_JOURNALENTRY`

### Configuration & Authorisation Permissions (for oversight)
- `READ_AUDIT`
- `READ_PERMISSION`
- `READ_ROLE`
- `READ_USER`
- `READ_CONFIGURATION`
- `READ_CODE`
- `READ_CODEVALUE`
- `READ_CURRENCY`
- `READ_DATATABLE`

## 4. Current Implemented Permissions (Initial Phase)

This section outlines the specific subset of permissions currently being implemented for each role in the initial phase.

### 4.1. Branch Manager (Initial Permissions)

- `READ_CLIENT`
- `APPROVE_SAVINGSACCOUNT`
- `READ_SAVINGSACCOUNT`
- `READ_OFFICE`
- `READ_STAFF`
- `READ_SAVINGSPRODUCT`
- `ACTIVATE_SAVINGSACCOUNT`
- `READ_PERMISSION`
- `READ_ROLE`
- `READ_USER`
- `READ_CURRENCY`
- `ALLOCATECASHIER_TELLER`
- `ALLOCATECASHTOCASHIER_TELLER`
- `SETTLECASHFROMCASHIER_TELLER`

### 4.2. Account Manager (Initial Permissions)

- `CREATE_CLIENT`
- `ACTIVATE_CLIENT`
- `READ_CLIENT`
- `UPDATE_CLIENT`
- `CREATE_SAVINGSACCOUNT`
- `READ_SAVINGSACCOUNT`
- `READ_SAVINGSPRODUCT`
- `READ_OFFICE`
- `READ_STAFF`

### 4.3. Cashier (Initial Permissions)

- `READ_CLIENT`
- `READ_SAVINGSACCOUNT`
- `DEPOSIT_SAVINGSACCOUNT`
- `WITHDRAWAL_SAVINGSACCOUNT`