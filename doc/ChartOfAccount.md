Here’s a **resume (summary) Chart of Accounts (COA)** for a microfinance institution based on the OHADA system, including  **OHADA codes** , **examples with journal entries** for major MFI operations, and explanations of  **product-to-chart mappings** .

## 1. Chart of Accounts (COA) for Microfinance under OHADA

| OHADA Code | Account Name                    | COA Class   | Example Use                  |
| ---------- | ------------------------------- | ----------- | ---------------------------- |
| 1011       | Cash at Hand                    | 1 (Asset)   | Office cash                  |
| 1012       | Bank Accounts                   | 1 (Asset)   | Deposits at local banks      |
| 1321       | Loans to Clients                | 1 (Asset)   | Microloans, SME loans        |
| 1331       | Interest Receivable             | 1 (Asset)   | Accrued interest, not paid   |
| 1421       | Office Equipment & Fixed Assets | 1 (Asset)   | Computers, furniture         |
| 2211       | Client Deposits                 | 2 (Liab.)   | Voluntary/mandatory savings  |
| 2311       | Borrowings from Banks/Donors    | 2 (Liab.)   | Refi loans, donor credit     |
| 2331       | Interest Payable                | 2 (Liab.)   | Owed on borrowing            |
| 2941       | Other Liabilities               | 2 (Liab.)   | Unpaid bills, etc.           |
| 3111       | Share Capital                   | 3 (Equity)  | Owner/Shareholder funds      |
| 3121       | Retained Earnings               | 3 (Equity)  | Accumulated profits          |
| 4011       | Interest Income                 | 4 (Revenue) | Interest from loans          |
| 4021       | Fee and Commission Income       | 4 (Revenue) | Loan processing, withdrawal  |
| 4081       | Other Operating Income          | 4 (Revenue) | Sundry, grants, sales        |
| 5011       | Personnel Expenses              | 5 (Exp.)    | Salaries, benefits           |
| 5061       | Loan Loss Provisions            | 5 (Exp.)    | Reserves for bad loans       |
| 5111       | Office and General Expenses     | 5 (Exp.)    | Rent, utilities, supplies    |
| 5151       | Depreciation & Amortization     | 5 (Exp.)    | Asset wear-and-tear          |
| 5211       | Interest Expense                | 5 (Exp.)    | Owed on borrowings           |
| 6011       | Management Fees                 | 6 (Mgmt.)   | Board/exec comp, consultants |
| 6061       | Regulatory/Reporting Compliance | 6 (Mgmt.)   | Audit, legal, regulator      |

*Actual codes may be adjusted; customize per your local chart and OHADA guidance.*

## 2. Example Journal Entries With Explanations

## a) **Client Deposit**

| Date   | Account (Code)         | Debit  | Credit | Explanation                          |
| ------ | ---------------------- | ------ | ------ | ------------------------------------ |
| 2024… | Cash at Hand (1011)    | 50,000 |        | Physical cash received from client   |
|        | Client Deposits (2211) |        | 50,000 | Increase in liability owed to client |

## b) **Client Withdrawal**

| Date   | Account (Code)         | Debit  | Credit | Explanation                                |
| ------ | ---------------------- | ------ | ------ | ------------------------------------------ |
| 2024… | Client Deposits (2211) | 20,000 |        | Reduce liability as client withdraws funds |
|        | Cash at Hand (1011)    |        | 20,000 | Outflow of cash from MFI                   |

## c) **Loan Disbursement to Client (with Fee Deducted Upfront)**

Total loan: 500,000; Fee: 10,000; Net given: 490,000

| Date   | Account (Code)          | Debit   | Credit  | Explanation                      |
| ------ | ----------------------- | ------- | ------- | -------------------------------- |
| 2024… | Loans to Clients (1321) | 500,000 |         | Asset: receivable from client    |
|        | Cash at Hand (1011)     |         | 490,000 | Cash outflow                     |
|        | Fee Income (4021)       |         | 10,000  | Fee revenue, recognized up front |

## d) **Interest Accrual on Loan**

Interest accrued but not yet collected: 5,000

| Date   | Account (Code)             | Debit | Credit | Explanation                    |
| ------ | -------------------------- | ----- | ------ | ------------------------------ |
| 2024… | Interest Receivable (1331) | 5,000 |        | Asset: earned but not received |
|        | Interest Income (4011)     |       | 5,000  | Revenue: income recognized     |

## e) **Client Loan Repayment (Principal + Interest)**

Repayment: 30,000 (includes 5,000 interest, 25,000 principal)

| Date   | Account (Code)             | Debit  | Credit | Explanation                              |
| ------ | -------------------------- | ------ | ------ | ---------------------------------------- |
| 2024… | Cash at Hand (1011)        | 30,000 |        | Cash inflow from client                  |
|        | Loans to Clients (1321)    |        | 25,000 | Reduces amount client owes               |
|        | Interest Receivable (1331) |        | 5,000  | Interest settled from accrued receivable |

## f) **Owner Capital Injection**

| Date   | Account (Code)       | Debit     | Credit    | Explanation                  |
| ------ | -------------------- | --------- | --------- | ---------------------------- |
| 2024… | Bank Account (1012)  | 2,000,000 |           | New capital from owners      |
|        | Share Capital (3111) |           | 2,000,000 | Increases equity from owners |

## g) **Receiving Borrowed Funds for Lending**

| Date   | Account (Code)               | Debit     | Credit    | Explanation                          |
| ------ | ---------------------------- | --------- | --------- | ------------------------------------ |
| 2024… | Bank Account (1012)          | 1,000,000 |           | Cash inflow (loan from another bank) |
|        | Borrowings from Banks (2311) |           | 1,000,000 | Liability: to be repaid              |

## 3. Mapping: MFI Account Product to Chart of Account Type

| Microfinance Product    | OHADA COA Code          | Account Type (Class) | Typical Usage Example |
| ----------------------- | ----------------------- | -------------------- | --------------------- |
| Client Savings          | 2211 - Client Deposits  | 2 (Liability)        | Deposits/wallets      |
| Microloans              | 1321 - Loans to Clients | 1 (Asset)            | Loan portfolio        |
| Loan Interest Income    | 4011 - Interest Income  | 4 (Revenue)          | Interest earned       |
| Fee/Commission on Loans | 4021 - Fee Income       | 4 (Revenue)          | Processing fees       |
| Donor or Bank Funds     | 2311 - Borrowings       | 2 (Liability)        | Funding for lending   |
| Shareholder Investment  | 3111 - Share Capital    | 3 (Equity)           | Owner capital         |
| Salary and Wages        | 5011 - Personnel Exp    | 5 (Expense)          | Payroll               |
| Office Rent             | 5111 - Gen/Office Exp   | 5 (Expense)          | Administrative cost   |
| Loan Loss Reserve       | 5061 - Provisions       | 5 (Expense/Reserve)  | Risk coverage         |

## **Explanations**

* Each financial product links to one or more chart of account types. For example, when a client makes a repayment, the "Microloans" (asset) and "Loan Interest Income" (revenue) accounts are both involved.
* Savings accounts map to liability accounts because the institution owes these deposits to clients.
* Loans to clients are assets, as they represent money owed to the institution.
* Fees and interest are revenues, realized on the income statement.
* Funding sources (borrowing or donor) are liabilities since the institution owes repayment.
* Owner investments are equity.

 **For actual implementation** , customize account numbers and names to fit your Fineract/MIS setup while matching the OHADA structure. Using this mapping ensures regulatory compliance, facilitates audits, and enables accurate, transparent reporting per Cameroonian and regional standards.
