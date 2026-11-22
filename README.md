# MyFinancePal

[![Build](https://github.com/alexanderschnell/PersonalFinanceTracker/actions/workflows/build.yml/badge.svg)](https://github.com/alexanderschnell/PersonalFinanceTracker/actions/workflows/build.yml)

My personal finance app: a JavaFX desktop app for tracking income and expenses. I use it for my own money and keep improving it over time.

## Features

- **Dashboard:** total balance, income and expenses at a glance.
- **Transactions:** add, view and delete income and expense entries with a date, description, category and amount.
- **Summary:** charts that break down spending.
- **Local storage:** everything is saved in a local SQLite database. No cloud, no accounts.

## Running it

Requires **Java 21+**.

**Windows:** double-click `run.cmd`. It builds the app the first time, then starts it. Run `run.cmd rebuild` after changing code.

**Any OS:**

```bash
./mvnw package                                   # build
java -jar target/finance-tracker-1.0-SNAPSHOT.jar
# or during development:
./mvnw javafx:run
```

## Where my data lives

Data is stored **outside the project** so real finances never end up in git:

| OS | Location |
|---|---|
| Windows | `%USERPROFILE%\.myfinancepal\finance.db` |
| macOS / Linux | `~/.myfinancepal/finance.db` |

To try something out without touching real data, point the app at a throwaway database:

```bash
java -Dmyfinancepal.db=test.db -jar target/finance-tracker-1.0-SNAPSHOT.jar
```

Back up `finance.db` by copying it somewhere safe.

## Tech

Java 21 · JavaFX 21 · SQLite (sqlite-jdbc) · Maven

```
src/main/java/com/financetracker/
├── Launcher.java              # entry point for the runnable jar
├── Main.java                  # app shell: header, sidebar, dashboard
├── TransactionManager.java    # SQLite access + change listeners
├── Transaction.java           # model (INCOME / EXPENSE)
├── AddTransactionWindow.java
├── ViewTransactionsWindow.java
├── FinanceDashboard.java      # summary charts
└── ...
```

## Roadmap

Things I want to add as I keep using it:

- [x] Add / view / delete transactions
- [x] SQLite storage outside the repo
- [x] Summary charts
- [ ] Edit existing transactions
- [ ] **Import bank CSV exports** with automatic categorisation rules
- [ ] Manage my own categories
- [ ] Monthly budgets per category, with progress bars
- [ ] Recurring transactions (rent, subscriptions, paycheques)
- [ ] Filter by month or date range, and search
- [ ] Month-over-month trend charts
- [ ] Export to CSV and one-click backup
- [ ] Unit tests for the data layer
