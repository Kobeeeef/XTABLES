# Exit the script on any error
set -e

# Step 1: Clean up old build files
echo "Cleaning up old build files..."
rm -rf build dist *.egg-info

# Step 2: Build the package (source distribution and wheel)
echo "Building the package..."
python setup.py sdist bdist_wheel

# Step 3: Upload the package to PyPI using twine
echo "Uploading the package to PyPI..."
twine upload dist/*

echo "Package uploaded successfully!"
