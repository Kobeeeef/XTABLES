"use client"

import 'primereact/resources/themes/saga-blue/theme.css'; //theme
import 'primereact/resources/primereact.min.css'; //core css
import 'primeicons/primeicons.css';


import React, {useEffect, useRef, useState} from 'react';
import {DataTable,} from 'primereact/datatable';
import {FilterMatchMode} from 'primereact/api';
import {Column} from 'primereact/column';
import {Toast} from 'primereact/toast';
import {InputText} from "primereact/inputtext";
const net = require('net');
export default function RowExpansionDemo() {
    const [products, setProducts] = useState([]);
    const [expandedRows, setExpandedRows] = useState(null);
    const [globalFilterValue, setGlobalFilterValue] = useState('');
    const toast = useRef(null);
    const [loading, setLoading] = useState(true);
    const [filters, setFilters] = useState({
        global: {value: null, matchMode: FilterMatchMode.CONTAINS},
        name: {value: null, matchMode: FilterMatchMode.STARTS_WITH},
        key: {value: null, matchMode: FilterMatchMode.STARTS_WITH}
    });
    useEffect(() => {
        const client = new net.Socket();

        client.connect(1735, 'localhost', () => {
            console.log('Connected to server');

            // Send data to the server
            client.write('Hello, server!');

            // Handle data received from the server
            client.on('data', (data) => {
                console.log('Received data from server:', data.toString());
            });

            // Handle connection close
            client.on('close', () => {
                console.log('Connection closed');
            });
        });

        client.on('error', (err) => {
            console.error('Connection error:', err);
        });
    }, []);

    useEffect(() => {
        const originalJSON = {
            "SmartDashboard": {
                "data": {
                    "somevalue": {
                        "value": "488",
                        "data": {
                            "anothersubtable": {"value": "488"}
                        }
                    },
                    "sometable": {
                        "value": "Some Value"
                    }
                },
                "value": "OK"
            },
            "AnotherTable": {
                "data": {
                    "somevalue": {
                        "value": Math.random()
                    },
                    "sometable": {
                        "value": Math.random()
                    }
                },
                "value": "OK"
            },
        };
        let formatted = convertJSON(originalJSON);
        setProducts([])
    }, []);

    function convertJSON(json) {
        const transformRecursively = (obj, parentKey = '') => {
            return Object.entries(obj).map(([key, value]) => {
                // Create a new object for the array
                let transformed = {
                    key: parentKey ? `${parentKey}.${key}` : key,
                    name: key
                };

                if (typeof value === 'object' && value !== null) {
                    if (value.hasOwnProperty('value')) {
                        transformed.value = value.value;
                    }
                    // Recurse if there's nested data
                    if (value.data) {
                        transformed.data = transformRecursively(value.data, transformed.key);
                    }
                } else {
                    transformed.value = value;
                }

                return transformed;
            });
        };

        // Start the transformation with the top-level keys
        return transformRecursively(json);
    }


    const textEditor = (options) => {
        return <InputText type="text" value={options.value} autoFocus={true}
                          onChange={(e) => options.editorCallback(e.target.value)}
                          onKeyDown={(e) => e.stopPropagation()}/>;
    };

    const allowExpansion = (rowData) => {
        return rowData.data && Object.keys(rowData.data).length > 0;
    };
    const onCellEditComplete = (e) => {
        let {rowData, newValue, field, originalEvent: event} = e;
        if (field === "value") {
            let key = rowData.key;
        }
    }
    const rowExpansionTemplate = (data) => {
        return (
            <div className="p-3">
                <DataTable value={data.data} editMode={"cell"} expandedRows={expandedRows}
                           onRowToggle={(e) => setExpandedRows(e.data)}
                           rowExpansionTemplate={rowExpansionTemplate}
                           dataKey="key" tableStyle={{minWidth: '60rem'}}>
                    <Column expander={allowExpansion} style={{width: '5rem'}}/>
                    <Column field="name" header="" sortable/>
                    <Column field="value" header="" editor={textEditor} onCellEditComplete={onCellEditComplete}
                            sortable/>
                </DataTable>
            </div>
        );
    };

    const onGlobalFilterChange = (e) => {
        const value = e.target.value;
        let _filters = {...filters};

        _filters['global'].value = value;

        setFilters(_filters);
        setGlobalFilterValue(value);
    };
    const renderHeader = () => {
        return (
            <div>


                <div>
                    <div className="relative flex items-center mt-2">
        <span className="absolute">
        </span>
                        <input
                            type="text"
                            id="global_search"
                            placeholder="Name Search"
                            value={globalFilterValue}
                            onChange={onGlobalFilterChange}
                            className="block w-full py-2.5 text-gray-700 placeholder-gray-400/70 bg-white border border-gray-200 rounded-lg pl-11 pr-5 rtl:pr-11 rtl:pl-5 dark:bg-gray-900 dark:text-gray-300 dark:border-gray-600 focus:border-blue-400 dark:focus:border-blue-300 focus:ring-blue-300 focus:outline-none focus:ring focus:ring-opacity-40"
                        />
                    </div>
                </div>
            </div>
        );
    };
    const header = renderHeader();
    return (
        <div className="card">
            <Toast ref={toast}/>
            <DataTable value={products} editMode={"cell"} globalFilterFields={['name', 'value']} filters={filters}
                       filterDisplay={"row"} expandedRows={expandedRows} loading={loading}
                       onRowToggle={(e) => setExpandedRows(e.data)}
                       rowExpansionTemplate={rowExpansionTemplate}
                       dataKey="key" header={header} tableStyle={{minWidth: '60rem'}}
                       emptyMessage={"No Data Found"}>
                <Column expander={allowExpansion} style={{width: '5rem'}}/>
                <Column field="name" header="Name" sortable/>
                <Column field="value" header="Value" editor={textEditor} onCellEditComplete={onCellEditComplete}
                        sortable/>
            </DataTable>
        </div>
    );
}