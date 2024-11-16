def validate_key(key, throw_error):
    # Check if the key is null or empty
    if key is None:
        if throw_error:
            raise ValueError("Key cannot be null.")
        else:
            return False

    # Check if key contains spaces
    if " " in key:
        if throw_error:
            raise ValueError("Key cannot contain spaces.")
        else:
            return False

    # Check if key starts or ends with '.'
    if key.startswith(".") or key.endswith("."):
        if throw_error:
            raise ValueError("Key cannot start or end with '.'")
        else:
            return False

    # Check if key contains multiple consecutive '.'
    if ".." in key:
        if throw_error:
            raise ValueError("Key cannot contain multiple consecutive '.'")
        else:
            return False

    # Check if each part of the key separated by '.' is empty
    if key:
        parts = key.split(".")
        for part in parts:
            if not part:
                if throw_error:
                    raise ValueError("Key contains empty part(s).")
                else:
                    return False

    return True


def parse_string(s):
    # Check if the string starts and ends with \" and remove them
    if s.startswith('"\\"') and s.endswith('\\""'):
        s = s[3:-3]
    elif s.startswith('"') and s.endswith('"'):
        s = s[1:-1]
    return s


def validate_name(name, throw_error):
    # Check if the name is null or empty
    if name is None:
        if throw_error:
            raise ValueError("Name cannot be null.")
        else:
            return False

    # Check if the name contains spaces
    if " " in name:
        if throw_error:
            raise ValueError("Name cannot contain spaces.")
        else:
            return False

    # Check if the name contains '.'
    if "." in name:
        if throw_error:
            raise ValueError("Name cannot contain '.'")
        else:
            return False

    return True
