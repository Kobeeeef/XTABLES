# Exit the script on any error
set -e

# Step 1: Clean up old build files
echo "Cleaning up old build files..."
rm -rf build dist *.egg-info

# Step 2: Build the package (source distribution and wheel)
echo "Building the package..."
python setup.py sdist bdist_wheel

# Step 3: Set the PyPI API key as an environment variable
export TWINE_PASSWORD="pypi-AgEIcHlwaS5vcmcCJDY4M2RjZjc3LWU5ZmMtNDU1Ny1hZGIxLWRkODk0MTY3NTc2NwACKlszLCI0MDQzNWYxOC1hMzI0LTQ5NzktOTJhMy05YzMwZTY0NzRhNjkiXQAABiAFmTXM3BMd2AqBGl2Fr0lcJLwR9eIMSldWDNs-cnZMdQ"

# Step 4: Upload the package to PyPI using twine
echo "Uploading the package to PyPI..."
twine upload dist/* -u __token__ -p "$TWINE_PASSWORD"

echo "Package uploaded successfully!"
