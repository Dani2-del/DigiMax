import ctypes
import getpass
import sys
import urllib.parse
import winreg


DATABASES = {
    "ACCESO_MONGODB_URI": "digitrans_acceso",
    "TRANSPORTE_MONGODB_URI": "digitrans_transporte",
    "BILLETERA_MONGODB_URI": "digitrans_billetera",
    "RECARGAS_MONGODB_URI": "digitrans_recargas",
}


def main():
    if sys.platform != "win32":
        raise SystemExit("Este script solo funciona en Windows.")

    host = input("Host de Atlas (ejemplo: cluster0.xxxxx.mongodb.net): ").strip()
    username = input("Usuario de base de datos de Atlas: ").strip()
    password = getpass.getpass("Contraseña de Atlas (no se mostrará): ")

    if not host or not username or not password or any(char in host for char in "/:@?#"):
        raise SystemExit("Host, usuario o contraseña inválidos. Revisa los datos e intenta de nuevo.")

    encoded_username = urllib.parse.quote(username, safe="")
    encoded_password = urllib.parse.quote(password, safe="")

    try:
        with winreg.OpenKey(
            winreg.HKEY_CURRENT_USER,
            "Environment",
            0,
            winreg.KEY_SET_VALUE,
        ) as environment:
            for variable, database in DATABASES.items():
                uri = (
                    f"mongodb+srv://{encoded_username}:{encoded_password}@{host}/"
                    f"{database}?retryWrites=true&w=majority"
                )
                winreg.SetValueEx(environment, variable, 0, winreg.REG_SZ, uri)
    except OSError as error:
        raise SystemExit(f"No se pudieron guardar las variables en Windows: {error}") from error

    result = ctypes.c_ulong()
    ctypes.windll.user32.SendMessageTimeoutW(
        0xFFFF,
        0x001A,
        0,
        "Environment",
        0x0002,
        5000,
        ctypes.byref(result),
    )
    print("Variables guardadas en el entorno de usuario de Windows:")
    for variable in DATABASES:
        print(f"- {variable}")
    print("Cierra y vuelve a abrir PowerShell o VS Code para que los procesos las detecten.")


if __name__ == "__main__":
    main()
