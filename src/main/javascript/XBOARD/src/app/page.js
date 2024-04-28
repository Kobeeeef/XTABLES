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
import {Client} from '@stomp/stompjs';

export default function Main() {
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
        function connect() {
            const client = new Client({
                brokerURL: 'ws://localhost:8080/websocket',
                onConnect: () => {
                    client.subscribe('/topic/update', message =>
                        console.log(`Received: ${message.body}`)
                    );
                }
            })
            client.onDisconnect(() => {
                console.log("Disconnected from client. Reconnecting")
                client.deactivate({force: true}).then(r =>{
                    return connect();
                }) ;
            })
            client.activate();
        }

        connect();
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
            toast.current.show({severity: 'info', summary: 'Request Sent!', detail: newValue, life: 5000})
        }
        return true;
    }

    const rowExpansionTemplate = (data) => {
        return (
            <div className="p-3">
                <DataTable showGridlines value={data.data} editMode={"cell"} expandedRows={expandedRows}
                           onRowToggle={(e) => setExpandedRows(e.data)}
                           rowExpansionTemplate={rowExpansionTemplate}
                           dataKey="key">
                    <Column expander={allowExpansion} style={{width: '5rem'}}/>
                    <Column field="name" header="" sortable/>
                    <Column field="value" header="" frozen={true} className="font-bold" editor={textEditor}
                            onCellEditComplete={onCellEditComplete}
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
        <div className="w-1/2 h-screen bg-gray-300">
            <Toast ref={toast}/>
            <DataTable value={products} showGridlines editMode={"cell"} globalFilterFields={['name', 'value']}
                       filters={filters}
                       filterDisplay={"row"} expandedRows={expandedRows} loading={loading}
                       onRowToggle={(e) => setExpandedRows(e.data)}
                       rowExpansionTemplate={rowExpansionTemplate}
                       className={"w-full h-full"}
                       dataKey="key" header={header} tableStyle={{minWidth: '15rem'}}
                       emptyMessage={"No Data Found"}>
                <Column expander={allowExpansion} style={{width: '5rem'}}/>
                <Column field="name" header="Name" sortable/>
                <Column field="value" header="Value" className="font-bold" frozen={true} editor={textEditor}
                        onCellEditComplete={onCellEditComplete}
                        sortable/>
            </DataTable>
        </div>
    );
}
