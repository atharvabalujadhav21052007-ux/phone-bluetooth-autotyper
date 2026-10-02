package com.example.bluetoothkeyboard

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class CodeSnippet(
    val id: String,
    val title: String,
    val language: String,
    val code: String,
    val category: String
)

class SnippetVaultManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("snippet_vault_prefs", Context.MODE_PRIVATE)
    private val KEY_SNIPPETS = "saved_snippets"

    init {
        if (!prefs.contains(KEY_SNIPPETS)) {
            loadDefaultSnippets()
        }
    }

    fun getAllSnippets(): List<CodeSnippet> {
        val jsonString = prefs.getString(KEY_SNIPPETS, null) ?: return emptyList()
        val list = mutableListOf<CodeSnippet>()
        try {
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    CodeSnippet(
                        id = obj.optString("id", System.currentTimeMillis().toString()),
                        title = obj.optString("title", "Untitled"),
                        language = obj.optString("language", "General"),
                        code = obj.optString("code", ""),
                        category = obj.optString("category", "Templates")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveSnippet(snippet: CodeSnippet) {
        val list = getAllSnippets().toMutableList()
        val existingIndex = list.indexOfFirst { it.id == snippet.id }
        if (existingIndex >= 0) {
            list[existingIndex] = snippet
        } else {
            list.add(0, snippet)
        }
        persistList(list)
    }

    fun deleteSnippet(id: String) {
        val list = getAllSnippets().filter { it.id != id }
        persistList(list)
    }

    private fun persistList(list: List<CodeSnippet>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("title", item.title)
            obj.put("language", item.language)
            obj.put("code", item.code)
            obj.put("category", item.category)
            array.put(obj)
        }
        prefs.edit().putString(KEY_SNIPPETS, array.toString()).apply()
    }

    private fun loadDefaultSnippets() {
        val defaults = listOf(
            CodeSnippet(
                id = "1",
                title = "Two Sum (Hash Map)",
                language = "Java",
                category = "DSA",
                code = "class Solution {\n    public int[] twoSum(int[] nums, int target) {\n        Map<Integer, Integer> map = new HashMap<>();\n        for (int i = 0; i < nums.length; i++) {\n            int complement = target - nums[i];\n            if (map.containsKey(complement)) {\n                return new int[] { map.get(complement), i };\n            }\n            map.put(nums[i], i);\n        }\n        return new int[] {};\n    }\n}"
            ),
            CodeSnippet(
                id = "2",
                title = "Binary Search (Standard)",
                language = "C++",
                category = "DSA",
                code = "int binarySearch(vector<int>& nums, int target) {\n    int left = 0, right = nums.size() - 1;\n    while (left <= right) {\n        int mid = left + (right - left) / 2;\n        if (nums[mid] == target) return mid;\n        else if (nums[mid] < target) left = mid + 1;\n        else right = mid - 1;\n    }\n    return -1;\n}"
            ),
            CodeSnippet(
                id = "3",
                title = "BFS Graph Traversal",
                language = "Python",
                category = "Algorithms",
                code = "from collections import deque\n\ndef bfs(graph, start):\n    visited = set([start])\n    queue = deque([start])\n    while queue:\n        node = queue.popleft()\n        print(node, end=' ')\n        for neighbor in graph[node]:\n            if neighbor not in visited:\n                visited.add(neighbor)\n                queue.append(neighbor)"
            ),
            CodeSnippet(
                id = "4",
                title = "Fast I/O & Boilerplate",
                language = "Java",
                category = "Template",
                code = "import java.io.*;\nimport java.util.*;\n\npublic class Main {\n    public static void main(String[] args) throws IOException {\n        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));\n        StringTokenizer st = new StringTokenizer(br.readLine());\n        int n = Integer.parseInt(st.nextToken());\n        System.out.println(n);\n    }\n}"
            ),
            CodeSnippet(
                id = "5",
                title = "SQL Aggregation & Window Function",
                language = "SQL",
                category = "Database",
                code = "SELECT department_id, employee_id, salary,\n       RANK() OVER (PARTITION BY department_id ORDER BY salary DESC) as rank_in_dept\nFROM employees\nWHERE status = 'ACTIVE';"
            )
        )
        persistList(defaults)
    }
}
