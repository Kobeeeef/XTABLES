from setuptools import setup, find_packages

setup(
    name="XTablesClient",
    version="5.1.1-SNAPSHOT",
    description="A high-performance Python client for real-time management of XTablesServer network tables, designed for "
                "robotics and complex data-driven systems.",
    long_description=open('README.md').read(),  # Add a README file
    long_description_content_type="text/markdown",
    author="Kobe Lei",
    author_email="kobelei335@gmail.com",
    url="https://github.com/Kobeeeef/XTABLES",  # Replace with your repo
    packages=find_packages(),
    install_requires=open('requirements.txt', encoding='utf-16').read().splitlines(),
    classifiers=[
        "Programming Language :: Python :: 3",
        "License :: OSI Approved :: MIT License",
        "Operating System :: OS Independent",
    ],
    python_requires=">=3.6",
)
