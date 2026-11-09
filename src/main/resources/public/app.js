(() => {
  const $ = (s) => document.querySelector(s);
  const inr = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" });
  const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  let accounts = [];

  async function api(method, url, body) {
    const res = await fetch(url, {
      method,
      headers: body ? { "Content-Type": "application/json" } : {},
      body: body ? JSON.stringify(body) : undefined,
    });
    if (res.status === 204) return null;
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(data.error || "Request failed (" + res.status + ")");
    return data;
  }

  let toastTimer;
  function toast(msg, isError = false) {
    const t = $("#toast");
    t.textContent = msg;
    t.className = "toast" + (isError ? " error" : "");
    t.dataset.state = isError ? "error" : "success";
    t.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => (t.hidden = true), 4000);
  }

  // Wraps a form submit: disables the submit button and shows errors as a toast.
  function onSubmit(form, handler) {
    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      const btns = form.querySelectorAll("button[type=submit]");
      btns.forEach((b) => (b.disabled = true));
      try { await handler(new FormData(form), e.submitter); }
      catch (err) { toast(err.message, true); }
      finally { btns.forEach((b) => (b.disabled = false)); }
    });
  }

  // ---------------- views
  function showAuth() {
    $("#auth-view").hidden = false;
    $("#dash-view").hidden = true;
    $("#user-box").hidden = true;
  }

  async function showDashboard(user) {
    $("#user-name").textContent = user.fullName;
    $("#auth-view").hidden = true;
    $("#dash-view").hidden = false;
    $("#user-box").hidden = false;
    await refresh();
  }

  async function refresh() {
    accounts = await api("GET", "/api/accounts");
    const total = accounts.reduce((s, a) => s + Number(a.balance), 0);
    $("#total-balance").textContent = inr.format(total);
    $("#accounts").innerHTML = accounts.map((a) => `
      <div class="acct" data-testid="account-card" data-account="${esc(a.accountNumber)}">
        <div class="type">${esc(a.type)}</div>
        <div class="num" data-testid="account-number">${esc(a.accountNumber)}</div>
        <div class="bal" data-testid="account-balance">${inr.format(a.balance)}</div>
      </div>`).join("");
    const opts = accounts.map((a) => `<option value="${esc(a.accountNumber)}">${esc(a.accountNumber)} · ${esc(a.type)}</option>`).join("");
    for (const sel of ["[name=account]", "[name=from]", "#history-account"]) {
      const el = document.querySelector(sel);
      const prev = el.value;
      el.innerHTML = opts;
      if (accounts.some((a) => a.accountNumber === prev)) el.value = prev;
    }
    await loadHistory();
  }

  async function loadHistory() {
    const acc = $("#history-account").value;
    if (!acc) return;
    const rows = await api("GET", `/api/accounts/${encodeURIComponent(acc)}/transactions?limit=50`);
    const credit = (t) => t === "DEPOSIT" || t === "TRANSFER_IN";
    $("#history").innerHTML = rows.map((t) => `
      <tr data-testid="history-row">
        <td>${esc(new Date(t.createdAt).toLocaleString("en-IN"))}</td>
        <td data-testid="history-type">${esc(t.type.replace("_", " "))}</td>
        <td>${t.counterparty ? (credit(t.type) ? "from " : "to ") + esc(t.counterparty) : ""}${t.note ? " · " + esc(t.note) : ""}</td>
        <td class="r ${credit(t.type) ? "cr" : "dr"}" data-testid="history-amount">${credit(t.type) ? "+" : "−"}${inr.format(t.amount)}</td>
        <td class="r">${inr.format(t.balanceAfter)}</td>
      </tr>`).join("");
    $("#history-empty").hidden = rows.length > 0;
  }

  // ---------------- events
  document.querySelectorAll(".tab").forEach((tab) =>
    tab.addEventListener("click", () => {
      document.querySelectorAll(".tab").forEach((t) => t.classList.toggle("active", t === tab));
      $("#login-form").hidden = tab.dataset.tab !== "login";
      $("#register-form").hidden = tab.dataset.tab !== "register";
    })
  );

  onSubmit($("#login-form"), async (f) => {
    const user = await api("POST", "/api/login", { username: f.get("username"), password: f.get("password") });
    $("#login-form").reset();
    await showDashboard(user);
  });

  onSubmit($("#register-form"), async (f) => {
    const user = await api("POST", "/api/register", {
      fullName: f.get("fullName"), username: f.get("username"), password: f.get("password"),
    });
    $("#register-form").reset();
    toast("Welcome! Your savings account is ready.");
    await showDashboard(user);
  });

  $("#logout-btn").addEventListener("click", async () => {
    await api("POST", "/api/logout");
    showAuth();
  });

  onSubmit($("#open-form"), async (f) => {
    const acc = await api("POST", "/api/accounts", { type: f.get("type") });
    toast(`Opened ${acc.type.toLowerCase()} account ${acc.accountNumber}`);
    await refresh();
  });

  onSubmit($("#money-form"), async (f, submitter) => {
    const action = submitter && submitter.value === "withdraw" ? "withdraw" : "deposit";
    const acc = await api("POST", `/api/accounts/${encodeURIComponent(f.get("account"))}/${action}`, { amount: f.get("amount") });
    $("#money-form [name=amount]").value = "";
    toast(`${action === "deposit" ? "Deposited" : "Withdrew"} successfully. New balance ${inr.format(acc.balance)}`);
    await refresh();
  });

  onSubmit($("#transfer-form"), async (f) => {
    const acc = await api("POST", "/api/transfer", {
      from: f.get("from"), to: f.get("to"), amount: f.get("amount"), note: f.get("note"),
    });
    ["to", "amount", "note"].forEach((n) => ($(`#transfer-form [name=${n}]`).value = ""));
    toast(`Transfer successful. New balance ${inr.format(acc.balance)}`);
    await refresh();
  });

  $("#history-account").addEventListener("change", () => loadHistory().catch((e) => toast(e.message, true)));

  // ---------------- boot
  api("GET", "/api/me").then(showDashboard).catch(showAuth);
})();
