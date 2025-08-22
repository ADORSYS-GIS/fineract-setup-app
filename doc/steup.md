
Based on our previous discussion, here is a comprehensive and consolidated guide for configuring Apache Fineract for a microfinance institution with three branches in the OHADA zone, focusing on a current account product. This guide incorporates all the requested details, including currency and payment methods.

### 1. Initial Organizational Setup & Branch Configuration

Start by building the organizational structure in Fineract.

* **Create the Head Office:** This is the top-level entity.
* **Create Three Branches:** Add three branch offices under the Head Office. Assign a unique name and code to each.
* **Define and Assign Staff:** Create user accounts for your employees. Assign them to their respective branches and give them a role with specific permissions.

---

### 2. Currency & Payment Methods Configuration

Before creating products, you must define the currencies and how money will move.

* **Configure Currency:**
  * Go to  **Admin > System > Currencies** .
  * Add the appropriate CFA Franc: **XOF** (West African CFA Franc) or **XAF** (Central African CFA Franc).
  * Set the  **Code** ,  **Name** , **Display Symbol** (e.g., FCFA), and **Decimal Places** (set to 2).
  * Enable the currency for institutional use.
* **Configure Payment Methods:**
  * Go to  **Admin > System > Payment Methods** .
  * Create a **"Cash"** payment method, which is essential for teller-based operations.
  * You can also configure other methods like **"Bank Transfer"** or **"Mobile Money"** if you plan to use them.

---

### 3. Product Configuration for the Current Account

This is where you define the features and rules for your current account.

* **Access Product Creation:** Go to  **Products > Savings Products > + Create Savings Product** .
* **Define Product Details:**
  * **Name:** "Current Account".
  * **Currency:** Select the CFA Franc you configured earlier.
  * **Interest Rate:** Set to  **0.00%** .
  * **Fees and Charges:** Configure fees like a monthly maintenance fee or transaction fees. Define the amount and frequency for each.
  * **Minimum Balance:** Set the minimum required balance.
  * **Overdraft Rules:** If offering an overdraft, define the credit limit, interest rate, and associated fees.
* **Transaction Limits (Product-Level):**
  * Set **Minimum/Maximum Deposit Amounts** and **Minimum/Maximum Withdrawal Amounts** to control transaction size for all clients using this product.

---

### 4. Accounting Rules and Chart of Accounts (COA)

This is vital for automatic and accurate financial tracking.

* **Configure Chart of Accounts (COA):** Create a COA that aligns with the **OHADA Uniform Act on Accounting Law** (SYSCOHADA). This involves setting up all necessary GL accounts for assets, liabilities, equity, income, and expenses.
* **Create Accounting Rules:**
  * Navigate to  **Accounting > Accounting Rules > + Add Rule** .
  * **Name the rule:** e.g., "Current Account Deposits".
  * **Define Debit/Credit:** For a deposit transaction:
    * **Debit:** The GL account for your bank's "Cash in Hand".
    * **Credit:** The GL account for "Client Savings" or "Current Account Liability".
  * **Link to Product:** Go back to the "Current Account" product, click  **Edit** , and link this accounting rule in the "Accounting" section.

---

### 5. Client, KYC, and Teller Management

This step involves setting up the client onboarding process and managing cash operations.

* **Client Identifiers & KYC:**
  * Go to **Admin > System > Manage Identifiers** to configure required IDs (e.g., National ID).
  * Use **Client/Group Documents** to specify and require the upload of KYC documents like proof of address.
* **Teller Roles and Permissions:**
  * Create a **"Teller"** role with specific, limited permissions.
  * **Grant these permissions:** Cashier Management (Open/Close Till), Transaction Processing (Deposit/Withdraw), and limited viewing of clients and accounts.
  * **Deny these permissions:** Administrative tasks, product configuration, and any access to the general ledger or advanced reporting.
* **Teller and Cashier Limits:**
  * Go to  **Admin > Organization > Teller / Cashier Management** .
  * Set a **"Max Cash In Hand"** limit for each teller's till.
  * Configure a **Daily Transaction Limit** on the total value of cash transactions a teller can handle.

---

### 6. Reporting and Compliance

Finally, ensure you can monitor performance and meet regulatory obligations.

* **Standard Reports:** Use Fineract's built-in reports to track key metrics like account balances, transaction volumes, and fees collected.
* **Custom Reports:** If needed, create custom reports to meet specific local or OHADA regulatory reporting requirements.
