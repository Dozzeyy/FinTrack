import os
import json
import sqlite3
import csv
import calendar
from datetime import datetime
from typing import Any

from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.screenmanager import ScreenManager, Screen
from kivy.uix.popup import Popup
from kivy.uix.label import Label
from kivy.properties import StringProperty, ListProperty
from kivy.clock import Clock
from kivy.lang import Builder
from kivy.uix.scrollview import ScrollView
from kivy.uix.gridlayout import GridLayout
from kivy.uix.button import Button
from kivy import platform

# Global path configuration
def get_data_dir():
    if platform == 'android':
        return App.get_running_app().user_data_dir
    return os.path.dirname(os.path.abspath(__file__))

# We'll initialize these in the App class to ensure they use the correct data dir
DB_FILE = "expenses.db"
BACKUP_FILE = "backup.json"

class HomeScreen(Screen):
    pass

class AddTransactionScreen(Screen):
    pass

class AddCategoryScreen(Screen):
    pass

class SelectTransactionTypeScreen(Screen):
    pass

class SummaryScreen(Screen):
    summary_text = StringProperty("")
    detail_text = StringProperty("")
    selected_category = StringProperty("All Categories")
    selected_account = StringProperty("All Accounts")
    start_date = StringProperty(None, allownone=True)
    end_date = StringProperty(None, allownone=True)

    def on_pre_enter(self, *args):
        self.update_filters()
        self.update_summary()

    def update_filters(self):
        app = App.get_running_app()
        self.ids.category_filter_spinner.values = app.get_category_list()
        self.ids.account_filter_spinner.values = app.get_account_list()

    def update_summary(self, start_date=None, end_date=None, category_name=None, account_name=None):
        app = App.get_running_app()
        db_path = app.get_db_path()
        conn = sqlite3.connect(db_path)
        cur = conn.cursor()

        # Update local filter state
        if start_date: self.start_date = start_date
        if end_date: self.end_date = end_date
        if category_name: self.selected_category = category_name
        if account_name: self.selected_account = account_name

        filters = []
        params = []

        if self.start_date and self.end_date:
            filters.append("t.date BETWEEN ? AND ?")
            params.extend([self.start_date, self.end_date])

        if self.selected_category and self.selected_category != "All Categories":
            filters.append("c.name = ?")
            params.append(self.selected_category)

        if self.selected_account and self.selected_account != "All Accounts":
            filters.append("a.name = ?")
            params.append(self.selected_account)

        where_clause = f"WHERE {' AND '.join(filters)}" if filters else ""

        # --- Category Totals ---
        cur.execute(f"""
            SELECT c.type, c.name, SUM(t.amount)
            FROM transactions t
            JOIN categories c ON t.category_id = c.id
            JOIN accounts a ON t.account_id = a.id
            {where_clause}
            GROUP BY c.type, c.name
            ORDER BY c.type, c.name
        """, params)
        category_totals = cur.fetchall()

        # --- Account Balances ---
        # Note: Balances are calculated based on all transactions regardless of category/account filters,
        # but respect the date filter if provided (as per user's original logic, but fixed for transfers)
        balance_where = ""
        balance_params = []
        if self.start_date and self.end_date:
            balance_where = "WHERE t.date BETWEEN ? AND ?"
            balance_params = [self.start_date, self.end_date]

        cur.execute(f"""
            SELECT a.name,
                a.opening_balance + COALESCE(SUM(
                    CASE
                        WHEN c.type = 'income' THEN t.amount
                        WHEN c.type = 'expense' THEN -t.amount
                        ELSE t.amount
                    END
                ), 0) AS balance
            FROM accounts a
            LEFT JOIN transactions t ON a.id = t.account_id
            LEFT JOIN categories c ON t.category_id = c.id
            {balance_where}
            GROUP BY a.name, a.opening_balance
            ORDER BY a.name
        """, balance_params)
        account_balances = cur.fetchall()

        # --- Daily Transaction List ---
        cur.execute(f"""
            SELECT t.date,
                COALESCE(c.name, 'Transfer') as category_name,
                a.name as account_name,
                t.amount,
                COALESCE(c.type, 'transfer') as category_type
            FROM transactions t
            LEFT JOIN categories c ON t.category_id = c.id
            JOIN accounts a ON t.account_id = a.id
            {where_clause}
            ORDER BY t.date ASC
        """, params)
        transactions = cur.fetchall()
        conn.close()

        # --- Format Summary ---
        summary_lines = ["[b]Category Totals:[/b]"]
        for cat_type, cat_name, total in category_totals:
            summary_lines.append(f"{cat_type.title()} - {cat_name}: {total:.2f}")

        summary_lines.append("\n[b]Account Balances:[/b]")
        for acc_name, balance in account_balances:
            summary_lines.append(f"{acc_name}: {balance:.2f}")

        self.summary_text = "\n".join(summary_lines)

        # --- Format Detail Transactions ---
        detail_lines = ["[b]Transactions by Date:[/b]"]
        current_date = None
        for date, category, account, amount, type_ in transactions:
            if date != current_date:
                detail_lines.append(f"\n[b]{date}[/b]")
                current_date = date

            color = {"income": "00FF00", "expense": "FF0000", "transfer": "0000FF"}.get(type_, "FFFFFF")
            display_amount = amount if type_ != "expense" else -amount
            detail_lines.append(
                f"  [color={color}]{category} ({account}): {display_amount:.2f}[/color]"
            )

        self.detail_text = "\n".join(detail_lines)

    def on_category_selected(self, category_name):
        self.update_summary(category_name=category_name)

    def on_account_selected(self, account_name):
        self.update_summary(account_name=account_name)

    def open_date_range_popup(self):
        popup = DateRangePopup(on_date_range_selected=self.update_summary_for_range)
        popup.open()

    def update_summary_for_range(self, start_date, end_date):
        start_str = start_date.strftime("%Y-%m-%d")
        end_str = end_date.strftime("%Y-%m-%d")
        self.update_summary(start_date=start_str, end_date=end_str)

class CalendarPopup(Popup):
    def __init__(self, on_date_selected=None, initial_date=None, **kwargs):
        super().__init__(**kwargs)
        self.title = "Select Date"
        self.size_hint = (0.9, 0.9)
        self.on_date_selected = on_date_selected
        if initial_date is None:
            now = datetime.now()
            self.selected_date = [now.year, now.month, now.day]
        else:
            self.selected_date = [initial_date.year, initial_date.month, initial_date.day]
        self.build_ui()

    def build_ui(self):
        main_layout = BoxLayout(orientation="vertical", padding=10, spacing=10)
        self.header_label = Label(text=self.get_month_year_text(), font_size=18, size_hint_y=None, height=40)
        prev_btn = Button(text="<", size_hint_y=None, height=40)
        next_btn = Button(text=">", size_hint_y=None, height=40)
        prev_btn.bind(on_release=lambda x: self.change_month(-1))
        next_btn.bind(on_release=lambda x: self.change_month(1))

        header_layout = BoxLayout(size_hint_y=None, height=40, spacing=10)
        header_layout.add_widget(prev_btn)
        header_layout.add_widget(self.header_label)
        header_layout.add_widget(next_btn)

        self.day_grid = GridLayout(cols=7, spacing=5, size_hint_y=None)
        self.day_grid.bind(minimum_height=self.day_grid.setter("height"))
        scroll = ScrollView(size_hint=(1, 1))
        scroll.add_widget(self.day_grid)

        btn_layout = BoxLayout(size_hint_y=None, height=50, spacing=10)
        ok_btn = Button(text="OK")
        cancel_btn = Button(text="Cancel")
        ok_btn.bind(on_release=self.confirm_date)
        cancel_btn.bind(on_release=lambda x: self.dismiss())
        btn_layout.add_widget(ok_btn)
        btn_layout.add_widget(cancel_btn)

        main_layout.add_widget(header_layout)
        main_layout.add_widget(scroll)
        main_layout.add_widget(btn_layout)
        self.content = main_layout
        self.populate_days()

    def get_month_year_text(self):
        return f"{calendar.month_name[self.selected_date[1]]} {self.selected_date[0]}"

    def change_month(self, offset):
        month = self.selected_date[1] + offset
        year = self.selected_date[0]
        if month < 1:
            month = 12
            year -= 1
        elif month > 12:
            month = 1
            year += 1
        self.selected_date[0] = year
        self.selected_date[1] = month
        self.header_label.text = self.get_month_year_text()
        self.populate_days()

    def populate_days(self):
        self.day_grid.clear_widgets()
        for day_name in ["Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"]:
            self.day_grid.add_widget(Label(text=day_name, bold=True, size_hint_y=None, height=30))

        month_days = calendar.monthcalendar(self.selected_date[0], self.selected_date[1])
        today = datetime.now()

        for week in month_days:
            for day in week:
                if day == 0:
                    self.day_grid.add_widget(Label(text="", size_hint_y=None, height=40))
                else:
                    btn = Button(
                        text=str(day),
                        size_hint_y=None,
                        height=40,
                        background_normal='',
                        background_color=(0.15, 0.15, 0.15, 1),
                        color=(1, 1, 1, 1)
                    )
                    if (day == today.day and self.selected_date[1] == today.month and self.selected_date[0] == today.year):
                        btn.background_color = (0, 0.6, 0, 1)
                    if day == self.selected_date[2]:
                        btn.background_color = (0, 0, 1, 1)

                    def select_day(instance, d=day):
                        self.selected_date[2] = d
                        self.populate_days()
                    btn.bind(on_release=select_day)
                    self.day_grid.add_widget(btn)

    def confirm_date(self, instance):
        if self.on_date_selected:
            selected = datetime(self.selected_date[0], self.selected_date[1], self.selected_date[2])
            self.on_date_selected(selected)
        self.dismiss()

class DateRangePopup(Popup):
    def __init__(self, on_date_range_selected=None, **kwargs):
        super().__init__(**kwargs)
        self.title = "Select Date Range"
        self.size_hint = (0.9, 0.9)
        self.on_date_range_selected = on_date_range_selected
        self.start_date = datetime.now()
        self.end_date = datetime.now()
        self.build_ui()

    def build_ui(self):
        layout = BoxLayout(orientation="vertical", spacing=10, padding=10)
        self.start_label = Label(text=f"Start Date: {self.start_date.strftime('%d/%m/%Y')}", size_hint_y=None, height=30)
        start_btn = Button(text="Select Start Date", size_hint_y=None, height=40)
        start_btn.bind(on_release=self.open_start_calendar)
        self.end_label = Label(text=f"End Date: {self.end_date.strftime('%d/%m/%Y')}", size_hint_y=None, height=30)
        end_btn = Button(text="Select End Date", size_hint_y=None, height=40)
        end_btn.bind(on_release=self.open_end_calendar)
        btn_layout = BoxLayout(size_hint_y=None, height=50, spacing=10)
        ok_btn = Button(text="OK")
        cancel_btn = Button(text="Cancel")
        ok_btn.bind(on_release=self.confirm)
        cancel_btn.bind(on_release=lambda x: self.dismiss())
        btn_layout.add_widget(ok_btn)
        btn_layout.add_widget(cancel_btn)
        layout.add_widget(self.start_label)
        layout.add_widget(start_btn)
        layout.add_widget(self.end_label)
        layout.add_widget(end_btn)
        layout.add_widget(btn_layout)
        self.content = layout

    def open_start_calendar(self, instance):
        popup = CalendarPopup(on_date_selected=self.set_start_date, initial_date=self.start_date)
        popup.open()

    def set_start_date(self, date):
        self.start_date = date
        self.start_label.text = f"Start Date: {date.strftime('%d/%m/%Y')}"

    def open_end_calendar(self, instance):
        popup = CalendarPopup(on_date_selected=self.set_end_date, initial_date=self.end_date)
        popup.open()

    def set_end_date(self, date):
        self.end_date = date
        self.end_label.text = f"End Date: {date.strftime('%d/%m/%Y')}"

    def confirm(self, instance):
        if self.on_date_range_selected:
            if self.start_date > self.end_date:
                self.start_date, self.end_date = self.end_date, self.start_date
            self.on_date_range_selected(self.start_date, self.end_date)
        self.dismiss()

class WindowManager(ScreenManager):
    pass

class ExpenseApp(App):
    transaction_type = StringProperty("")

    def build(self):
        self.init_db()
        self.sm = Builder.load_file("expense.kv")
        return self.sm

    def get_db_path(self):
        return os.path.join(self.user_data_dir, DB_FILE)

    def get_backup_path(self):
        return os.path.join(self.user_data_dir, BACKUP_FILE)

    def init_db(self):
        db_path = self.get_db_path()
        conn = sqlite3.connect(db_path)
        cur = conn.cursor()

        cur.execute('''CREATE TABLE IF NOT EXISTS accounts (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            type TEXT NOT NULL,
            opening_balance REAL DEFAULT 0
        )''')

        # Ensure opening_balance exists for older versions
        try:
            cur.execute("ALTER TABLE accounts ADD COLUMN opening_balance REAL DEFAULT 0")
        except sqlite3.OperationalError:
            pass

        cur.execute('''CREATE TABLE IF NOT EXISTS categories (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            type TEXT NOT NULL
        )''')

        cur.execute('''CREATE TABLE IF NOT EXISTS transactions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            date TEXT NOT NULL,
            account_id INTEGER,
            category_id INTEGER,
            amount REAL NOT NULL,
            note TEXT,
            FOREIGN KEY(account_id) REFERENCES accounts(id),
            FOREIGN KEY(category_id) REFERENCES categories(id)
        )''')

        # Default values
        cur.execute("SELECT COUNT(*) FROM accounts")
        if cur.fetchone()[0] == 0:
            default_accounts = [("Savings Bank", "asset"), ("Cash", "asset"), ("Investments", "asset"), ("Credit Cards", "liability")]
            cur.executemany("INSERT INTO accounts (name, type) VALUES (?, ?)", default_accounts)

        cur.execute("SELECT COUNT(*) FROM categories")
        if cur.fetchone()[0] == 0:
            expense_categories = ["Apps", "Clothing", "Education", "Electronics", "Entertainment", "Food 3 time", "Food online", "Food Snacks", "Food Healthy", "Groceries", "Home Telephone", "Social", "Telephone", "Travel", "Travel Distance", "Misc"]
            income_categories = ["Interest Income", "Investment Income", "Prof Fees", "Lucky Reward", "Salary"]
            for cat in expense_categories:
                cur.execute("INSERT INTO categories (name, type) VALUES (?, ?)", (cat, "expense"))
            for cat in income_categories:
                cur.execute("INSERT INTO categories (name, type) VALUES (?, ?)", (cat, "income"))

        conn.commit()
        conn.close()

    def schedule_prepare_add_transaction(self):
        Clock.schedule_once(lambda dt: self.prepare_add_transaction(), 0)

    def add_category(self):
        screen = self.root.get_screen("add_category")
        name = screen.ids.category_name_input.text.strip()
        ctype = screen.ids.category_type_spinner.text.strip()

        if not name:
            self.show_popup("Error", "Name cannot be empty.")
            return

        db_path = self.get_db_path()
        conn = sqlite3.connect(db_path)
        cur = conn.cursor()

        if ctype == "accounts":
            opening_balance_str = screen.ids.opening_balance_input.text.strip()
            try:
                opening_balance = float(opening_balance_str) if opening_balance_str else 0
            except ValueError:
                self.show_popup("Error", "Invalid opening balance")
                conn.close()
                return

            cur.execute("SELECT id FROM accounts WHERE name=?", (name,))
            if cur.fetchone():
                self.show_popup("Error", f"Account '{name}' already exists.")
            else:
                cur.execute("INSERT INTO accounts (name, type, opening_balance) VALUES (?, ?, ?)", (name, "asset", opening_balance))
                conn.commit()
                self.show_popup("Success", f"Account '{name}' added.")
        elif ctype in ("income", "expense"):
            cur.execute("SELECT id FROM categories WHERE name=? AND type=?", (name, ctype))
            if cur.fetchone():
                self.show_popup("Error", f"Category '{name}' exists.")
            else:
                cur.execute("INSERT INTO categories (name, type) VALUES (?, ?)", (name, ctype))
                conn.commit()
                self.show_popup("Success", f"Category '{name}' added.")
        else:
            self.show_popup("Error", "Select a valid type.")

        conn.close()
        screen.ids.category_name_input.text = ""
        screen.ids.opening_balance_input.text = ""
        self.root.current = "home"

    def get_account_list(self):
        conn = sqlite3.connect(self.get_db_path())
        cur = conn.cursor()
        cur.execute("SELECT name FROM accounts ORDER BY name")
        accounts = [row[0] for row in cur.fetchall()]
        conn.close()
        return ["All Accounts"] + accounts

    def get_category_list(self):
        conn = sqlite3.connect(self.get_db_path())
        cur = conn.cursor()
        cur.execute("SELECT DISTINCT name FROM categories ORDER BY name")
        categories = [row[0] for row in cur.fetchall()]
        conn.close()
        return ["All Categories"] + categories

    def open_remove_category_popup(self):
        # Alias for consistency with KV
        self.open_remove_item_popup()

    def open_remove_item_popup(self):
        conn = sqlite3.connect(self.get_db_path())
        cur = conn.cursor()
        cur.execute("SELECT id, name, type FROM categories")
        categories = cur.fetchall()
        cur.execute("SELECT id, name, type FROM accounts")
        accounts = cur.fetchall()
        conn.close()

        combined_list = [('category', cid, cname, ctype) for cid, cname, ctype in categories]
        combined_list += [('account', aid, aname, atype) for aid, aname, atype in accounts]

        layout = GridLayout(cols=1, spacing=10, size_hint_y=None)
        layout.bind(minimum_height=layout.setter('height'))
        scroll = ScrollView(size_hint=(1, 1))
        scroll.add_widget(layout)

        popup = Popup(title="Remove Item", content=scroll, size_hint=(0.8, 0.8))

        def do_remove(instance, item_type, item_id):
            self.remove_item(item_type, item_id, popup)

        for item_type, item_id, item_name, item_subtype in combined_list:
            btn = Button(text=f"{item_name} ({item_type})", size_hint_y=None, height=40)
            btn.bind(on_release=lambda btn, t=item_type, i=item_id: do_remove(btn, t, i))
            layout.add_widget(btn)
        popup.open()

    def remove_item(self, item_type, item_id, popup):
        conn = sqlite3.connect(self.get_db_path())
        cur = conn.cursor()
        if item_type == 'category':
            cur.execute("DELETE FROM categories WHERE id=?", (item_id,))
        else:
            cur.execute("DELETE FROM accounts WHERE id=?", (item_id,))
        conn.commit()
        conn.close()
        popup.dismiss()
        self.show_popup("Success", f"Removed successfully.")

    def prepare_add_transaction(self):
        sm = self.root
        screen = sm.get_screen("add")
        screen.ids.amount_input.text = ""
        screen.ids.note_input.text = ""
        screen.ids.date_input.text = datetime.now().strftime("%d/%m/%Y")

        conn = sqlite3.connect(self.get_db_path())
        cur = conn.cursor()

        if self.transaction_type in ("income", "expense"):
            cur.execute("SELECT name FROM categories WHERE type=?", (self.transaction_type,))
            categories = [row[0] for row in cur.fetchall()]
            screen.ids.category_spinner.values = categories
            screen.ids.category_spinner.text = categories[0] if categories else "No categories"
            cur.execute("SELECT name FROM accounts")
            accounts = [row[0] for row in cur.fetchall()]
            screen.ids.account_spinner.values = accounts
            screen.ids.account_spinner.text = accounts[0] if accounts else "No accounts"
        elif self.transaction_type == "transfer":
            cur.execute("SELECT name FROM accounts")
            accounts = [row[0] for row in cur.fetchall()]
            screen.ids.from_account_spinner.values = accounts
            screen.ids.to_account_spinner.values = accounts
            if len(accounts) >= 2:
                screen.ids.from_account_spinner.text = accounts[0]
                screen.ids.to_account_spinner.text = accounts[1]
            else:
                screen.ids.from_account_spinner.text = accounts[0] if accounts else "No accounts"
                screen.ids.to_account_spinner.text = accounts[0] if accounts else "No accounts"
        conn.close()
        screen.ids.form_title.text = f"Add {self.transaction_type.capitalize()}"

    def get_current_date_display(self):
        return datetime.now().strftime("%d/%m/%Y")

    def open_calendar_popup(self):
        def on_date_selected(selected_date):
            self.root.get_screen("add").ids.date_input.text = selected_date.strftime("%d/%m/%Y")
        current_text = self.root.get_screen("add").ids.date_input.text
        try:
            current_date = datetime.strptime(current_text, "%d/%m/%Y")
        except:
            current_date = datetime.now()
        CalendarPopup(on_date_selected=on_date_selected, initial_date=current_date).open()

    def save_transaction(self):
        screen = self.root.get_screen("add")
        date_str = screen.ids.date_input.text.strip()
        try:
            date_obj = datetime.strptime(date_str, "%d/%m/%Y")
            date_for_db = date_obj.strftime("%Y-%m-%d")
        except:
            self.show_popup("Error", "Invalid date.")
            return

        if self.transaction_type == "transfer":
            from_acc = screen.ids.from_account_spinner.text
            to_acc = screen.ids.to_account_spinner.text
            amount_str = screen.ids.amount_input.text.strip()
            note = screen.ids.note_input.text.strip()
            if from_acc == to_acc or "No" in from_acc or "No" in to_acc:
                self.show_popup("Error", "Invalid account selection.")
                return
            try:
                amount = float(amount_str)
                if amount <= 0: raise ValueError
            except:
                self.show_popup("Error", "Invalid amount.")
                return
            self.transfer_funds(from_acc, to_acc, amount, note, date_for_db)
            screen.manager.current = "home"
            return

        category_name = screen.ids.category_spinner.text
        account_name = screen.ids.account_spinner.text
        amount_str = screen.ids.amount_input.text.strip()
        note = screen.ids.note_input.text.strip()

        if "No" in category_name or "No" in account_name:
            self.show_popup("Error", "Select category and account.")
            return
        try:
            amount = float(amount_str)
        except:
            self.show_popup("Error", "Invalid amount.")
            return

        conn = sqlite3.connect(self.get_db_path())
        cur = conn.cursor()
        cur.execute("SELECT id FROM categories WHERE name=? AND type=?", (category_name, self.transaction_type))
        res_cat = cur.fetchone()
        cur.execute("SELECT id FROM accounts WHERE name=?", (account_name,))
        res_acc = cur.fetchone()

        if res_cat and res_acc:
            cur.execute("INSERT INTO transactions (date, account_id, category_id, amount, note) VALUES (?, ?, ?, ?, ?)",
                        (date_for_db, res_acc[0], res_cat[0], amount, note))
            conn.commit()
            self.show_popup("Success", "Transaction saved.")
            screen.manager.current = "home"
        else:
            self.show_popup("Error", "Data error.")
        conn.close()

    def transfer_funds(self, from_acc, to_acc, amount, note, date):
        conn = sqlite3.connect(self.get_db_path())
        cur = conn.cursor()
        cur.execute("INSERT INTO transactions (date, amount, category_id, account_id, note) VALUES (?, ?, NULL, (SELECT id FROM accounts WHERE name=?), ?)",
                    (date, -amount, from_acc, note))
        cur.execute("INSERT INTO transactions (date, amount, category_id, account_id, note) VALUES (?, ?, NULL, (SELECT id FROM accounts WHERE name=?), ?)",
                    (date, amount, to_acc, note))
        conn.commit()
        conn.close()
        self.show_popup("Success", "Transfer completed.")

    def show_popup(self, title, message):
        Popup(title=title, content=Label(text=message), size_hint=(0.7, 0.3)).open()

    def export_csv_with_date_range(self):
        def on_dates_selected(start_date, end_date):
            path = self.export_csv_filtered(start_date.strftime("%Y-%m-%d"), end_date.strftime("%Y-%m-%d"))
            if path: self.show_popup("Exported", f"CSV saved to: {path}")
        DateRangePopup(on_date_range_selected=on_dates_selected).open()

    def export_csv_filtered(self, start_date, end_date):
        db_path = self.get_db_path()
        conn = sqlite3.connect(db_path)
        cur = conn.cursor()
        cur.execute("""
            SELECT t.date, a.name, COALESCE(c.name, 'Transfer'), COALESCE(c.type, 'transfer'), t.amount, t.note
            FROM transactions t JOIN accounts a ON t.account_id = a.id
            LEFT JOIN categories c ON t.category_id = c.id
            WHERE t.date BETWEEN ? AND ? ORDER BY t.date ASC
        """, (start_date, end_date))
        rows = cur.fetchall()
        conn.close()

        file_path = os.path.join(self.user_data_dir, "transactions_export.csv")
        try:
            with open(file_path, "w", newline='') as f:
                writer = csv.writer(f)
                writer.writerow(["Date", "Account", "Category", "Type", "Amount", "Note"])
                writer.writerows(rows)
            return file_path
        except Exception as e:
            self.show_popup("Error", str(e))
            return None

    def backup_data(self):
        conn = sqlite3.connect(self.get_db_path())
        cur = conn.cursor()
        data = {}
        for table in ["accounts", "categories", "transactions"]:
            cur.execute(f"SELECT * FROM {table}")
            cols = [desc[0] for desc in cur.description]
            data[table] = [dict(zip(cols, row)) for row in cur.fetchall()]
        conn.close()
        try:
            with open(self.get_backup_path(), "w") as f:
                json.dump(data, f)
            self.show_popup("Backup", "Backup saved.")
        except Exception as e:
            self.show_popup("Error", str(e))

    def restore_data(self):
        path = self.get_backup_path()
        if not os.path.exists(path):
            self.show_popup("Restore", "No backup found.")
            return
        try:
            with open(path, "r") as f:
                data = json.load(f)
            conn = sqlite3.connect(self.get_db_path())
            cur = conn.cursor()
            for table in ["accounts", "categories", "transactions"]:
                cur.execute(f"DELETE FROM {table}")
            for table, rows in data.items():
                if rows:
                    keys = rows[0].keys()
                    cur.executemany(f"INSERT INTO {table} ({','.join(keys)}) VALUES ({','.join(['?']*len(keys))})",
                                   [tuple(r.values()) for r in rows])
            conn.commit()
            conn.close()
            self.show_popup("Restore", "Restored successfully.")
        except Exception as e:
            self.show_popup("Error", str(e))

if __name__ == "__main__":
    ExpenseApp().run()
